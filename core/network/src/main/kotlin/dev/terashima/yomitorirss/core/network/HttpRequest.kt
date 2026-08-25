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
  val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
  val maxErrorResponseBytes: Long = maxResponseBytes,
) {
  init {
    require(maxResponseBytes in 1..Int.MAX_VALUE.toLong()) { "maxResponseBytes は1以上Int.MAX_VALUE以下である必要があります" }
    require(maxErrorResponseBytes in 1..Int.MAX_VALUE.toLong()) {
      "maxErrorResponseBytes は1以上Int.MAX_VALUE以下である必要があります"
    }
  }
}

/** 用途が上限を明示しない場合にも、応答を無制限には読み込まない。 */
private const val DEFAULT_MAX_RESPONSE_BYTES = 1024L * 1024L
