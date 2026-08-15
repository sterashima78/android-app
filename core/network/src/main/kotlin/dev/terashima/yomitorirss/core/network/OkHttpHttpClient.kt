package dev.terashima.yomitorirss.core.network

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
  private val client: OkHttpClient = defaultOkHttpClient(),
) : HttpClient {
  override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
    val requestBody = when (request.method) {
      HttpMethod.GET -> null
      else -> (request.body ?: byteArrayOf()).toRequestBody(request.contentType?.toMediaTypeOrNull())
    }
    val okhttpRequest = Request.Builder()
      .url(request.url)
      .header("User-Agent", USER_AGENT)
      .apply {
        request.headers.forEach { (name, value) -> header(name, value) }
      }
      .method(request.method.name, requestBody)
      .build()

    try {
      client.newCall(okhttpRequest).execute().use { response ->
        HttpResponse(
          statusCode = response.code,
          reasonPhrase = response.message,
          finalUrl = response.request.url.toString(),
          headers = response.headers.toMultimap(),
          body = response.body.bytes(),
        )
      }
    } catch (error: IOException) {
      throw error.toNetworkError()
    }
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

private const val USER_AGENT = "Yomitori-RSS-Reader/0.2 (Android)"
