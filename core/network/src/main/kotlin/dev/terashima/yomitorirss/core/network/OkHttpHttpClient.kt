package dev.terashima.yomitorirss.core.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class OkHttpHttpClient(
  private val client: OkHttpClient,
  private val userAgent: String,
) : HttpClient {
  override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
    val requestBody = when (request.method) {
      HttpMethod.GET -> null
      else -> (request.body ?: byteArrayOf()).toRequestBody(request.contentType?.toMediaTypeOrNull())
    }
    val okhttpRequest = Request.Builder()
      .url(request.url)
      .header("User-Agent", userAgent)
      .apply {
        request.headers.forEach { (name, value) -> header(name, value) }
      }
      .method(request.method.name, requestBody)
      .build()

    try {
      client.newCall(okhttpRequest).execute().use { response ->
        val maxResponseBytes = if (response.isSuccessful) {
          request.maxResponseBytes
        } else {
          request.maxErrorResponseBytes
        }
        val responseBody = response.body
        val contentLength = responseBody.contentLength()
        if (contentLength > maxResponseBytes) {
          throw ResponseTooLargeException(maxResponseBytes, contentLength)
        }
        HttpResponse(
          statusCode = response.code,
          reasonPhrase = response.message,
          finalUrl = response.request.url.toString(),
          headers = response.headers.toMultimap(),
          body = responseBody.byteStream().use { input ->
            val output = ByteArrayOutputStream(minOf(contentLength.coerceAtLeast(0L), maxResponseBytes).toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
              val readBytes = input.read(buffer)
              if (readBytes == -1) break
              totalBytes += readBytes
              if (totalBytes > maxResponseBytes) {
                throw ResponseTooLargeException(maxResponseBytes, contentLength.takeIf { it >= 0L })
              }
              output.write(buffer, 0, readBytes)
            }
            output.toByteArray()
          },
        )
      }
    } catch (error: IOException) {
      throw error.toNetworkError()
    }
  }
}

internal object OkHttpHttpClientFactory {
  private val transport: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    defaultOkHttpClient()
  }
  private val clients = mutableMapOf<String, HttpClient>()

  @Synchronized
  fun create(userAgent: String): HttpClient = clients.getOrPut(userAgent) {
    OkHttpHttpClient(
      client = transport,
      userAgent = userAgent,
    )
  }
}

private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
  .connectTimeout(20, TimeUnit.SECONDS)
  .readTimeout(30, TimeUnit.SECONDS)
  .callTimeout(45, TimeUnit.SECONDS)
  .followRedirects(true)
  .followSslRedirects(true)
  .build()

private fun IOException.toNetworkError(): IOException = when (this) {
  is SocketTimeoutException -> IOException("ネットワーク通信がタイムアウトしました", this)
  is UnknownHostException -> IOException("ホスト名を解決できませんでした", this)
  is ConnectException -> IOException("サーバーに接続できませんでした", this)
  else -> IOException("ネットワーク通信に失敗しました: ${message ?: javaClass.simpleName}", this)
}

class ResponseTooLargeException(
  val maxResponseBytes: Long,
  val declaredContentLength: Long?,
) : IllegalStateException(
  if (declaredContentLength == null) {
    "レスポンスが上限（$maxResponseBytes バイト）を超えました"
  } else {
    "レスポンスのContent-Length（$declaredContentLength バイト）が上限（$maxResponseBytes バイト）を超えています"
  },
)
