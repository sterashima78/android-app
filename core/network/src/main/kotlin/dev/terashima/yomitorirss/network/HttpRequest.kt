package dev.terashima.yomitorirss.core.network

enum class HttpMethod {
  GET,
  POST,
  PUT,
  PATCH,
  DELETE,
}

data class HttpRequest(
  val url: String,
  val headers: Map<String, String> = emptyMap(),
  val method: HttpMethod = HttpMethod.GET,
  val body: ByteArray? = null,
  val contentType: String? = null,
)
