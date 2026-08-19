package dev.terashima.yomitorirss.feature.mail.data

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64

private val CHARSET_PARAMETER = Regex(
  "charset\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^;\\s]+))",
  RegexOption.IGNORE_CASE,
)

internal fun decodeMailBody(
  data: String,
  contentType: String,
): String = runCatching {
  val bytes = Base64.getUrlDecoder().decode(data)
  String(bytes, contentType.mailCharset())
}.getOrDefault("")

internal fun isDisplayMailBodyPart(
  filename: String,
  contentDisposition: String,
): Boolean = filename.isBlank() &&
  !contentDisposition.trimStart().startsWith("attachment", ignoreCase = true)

private fun String.mailCharset(): Charset {
  val match = CHARSET_PARAMETER.find(this)
  val charsetName = match
    ?.groupValues
    ?.drop(1)
    ?.firstOrNull(String::isNotBlank)
    ?.trim()
  if (charsetName == null) return StandardCharsets.UTF_8
  return runCatching { Charset.forName(charsetName) }.getOrDefault(StandardCharsets.UTF_8)
}
