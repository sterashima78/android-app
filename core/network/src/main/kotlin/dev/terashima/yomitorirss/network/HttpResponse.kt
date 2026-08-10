package dev.terashima.yomitorirss.core.network

data class HttpResponse(
  val statusCode: Int,
  val reasonPhrase: String,
  val finalUrl: String,
  val headers: Map<String, List<String>>,
  val body: ByteArray,
) {
  val isSuccessful: Boolean
    get() = statusCode in 200..299

  fun header(name: String): String? = headers.entries
    .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
    ?.value
    ?.lastOrNull()
}
