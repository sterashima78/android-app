package dev.terashima.yomitorirss.core.network

import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class OkHttpHttpClientTest {
  private val client = OkHttpHttpClient(OkHttpClient(), "test")

  @Test
  fun `Content-Length が上限を超える応答を拒否する`() = withServer(
    "HTTP/1.1 200 OK\r\nContent-Length: 11\r\n\r\n12345678901",
  ) { url ->
    val error = assertThrows(ResponseTooLargeException::class.java) {
      runBlocking { client.execute(HttpRequest(url, maxResponseBytes = 10)) }
    }
    assertEquals(10L, error.maxResponseBytes)
    assertEquals(11L, error.declaredContentLength)
  }

  @Test
  fun `Content-Length がない過大なchunked応答を拒否する`() = withServer(
    "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n6\r\n123456\r\n6\r\n789012\r\n0\r\n\r\n",
  ) { url ->
    val error = assertThrows(ResponseTooLargeException::class.java) {
      runBlocking { client.execute(HttpRequest(url, maxResponseBytes = 10)) }
    }
    assertNull(error.declaredContentLength)
  }

  @Test
  fun `上限ちょうどの応答を受け取る`() = withServer(
    "HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\n1234567890",
  ) { url ->
    val response = runBlocking { client.execute(HttpRequest(url, maxResponseBytes = 10)) }
    assertArrayEquals("1234567890".toByteArray(), response.body)
  }

  @Test
  fun `本文の途中で切断された応答を失敗にする`() = withServer(
    "HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\n12345",
  ) { url ->
    assertThrows(IOException::class.java) {
      runBlocking { client.execute(HttpRequest(url, maxResponseBytes = 10)) }
    }
  }

  @Test
  fun `正常な小容量応答を受け取る`() = withServer(
    "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK",
  ) { url ->
    val response = runBlocking { client.execute(HttpRequest(url, maxResponseBytes = 10)) }
    assertEquals(200, response.statusCode)
    assertArrayEquals("OK".toByteArray(), response.body)
  }

  private fun withServer(rawResponse: String, assertion: (String) -> Unit) {
    ServerSocket(0).use { server ->
      val serverThread = thread(name = "http-response-test-server") {
        server.accept().use { socket ->
          val input = socket.getInputStream().bufferedReader()
          while (!input.readLine().isNullOrEmpty()) {
            // リクエストヘッダーを末尾まで消費する。
          }
          socket.getOutputStream().apply {
            write(rawResponse.toByteArray(Charsets.US_ASCII))
            flush()
          }
        }
      }
      try {
        assertion("http://127.0.0.1:${server.localPort}/")
      } finally {
        serverThread.join(5_000)
      }
    }
  }
}
