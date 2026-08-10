package dev.terashima.yomitorirss.feature.article.data.network

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import org.jsoup.Jsoup
import java.net.URI
import java.nio.charset.Charset

class ArticleContentClient(
  private val client: HttpClient = HttpClient.create(),
) {
  suspend fun fetchArticleText(url: String): String {
    val response = client.execute(request(normalizeInputUrl(url)))
    response.requireSuccess()
    val html = decode(response.body, response.header("Content-Type"))
    val document = Jsoup.parse(html, response.finalUrl)
    document.select("script,style,noscript,nav,footer,header,form,aside").remove()
    val primary = document.selectFirst("article, main, [role=main]") ?: document.body()
    return primary.text().replace(Regex("\\s+"), " ").trim().take(40_000)
      .ifBlank { error("記事本文を取得できませんでした") }
  }

  private fun request(url: String): HttpRequest = HttpRequest(
    url = url,
    headers = mapOf("Accept" to "text/html, application/xhtml+xml;q=0.9, */*;q=0.5"),
  )

  private fun HttpResponse.requireSuccess() {
    if (!isSuccessful) error("HTTP $statusCode: $reasonPhrase")
  }

  private fun decode(bytes: ByteArray, contentType: String?): String {
    val headerCharset = contentType
      ?.substringAfter("charset=", "")
      ?.substringBefore(';')
      ?.trim(' ', '"', '\'')
      ?.takeIf(String::isNotBlank)
    val declaration = bytes.take(256).toByteArray().toString(Charsets.US_ASCII)
      .let { XML_ENCODING.find(it)?.groupValues?.getOrNull(1) }
    val charset = sequenceOf(headerCharset, declaration, "UTF-8")
      .mapNotNull { name -> runCatching { Charset.forName(name) }.getOrNull() }
      .first()
    return bytes.toString(charset).removePrefix("\uFEFF")
  }

  private fun normalizeInputUrl(input: String): String {
    val trimmed = input.trim()
    require(trimmed.isNotBlank()) { "URLを入力してください" }
    val candidate = when {
      trimmed.startsWith("https://", ignoreCase = true) -> trimmed
      trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.substring(7)}"
      else -> "https://$trimmed"
    }
    val uri = URI(candidate)
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) { "URLが正しくありません" }
    return uri.normalize().toString()
  }

  private companion object {
    val XML_ENCODING = Regex("encoding\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
  }
}
