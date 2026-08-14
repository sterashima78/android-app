package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class KindleAmazonCoverDiagnosticsTest {
  @Test
  fun `既知抽出器で取得できないscript内画像手掛かりを件数で記録する`() = runBlocking {
    val asin = "B0TEST0001"
    val html = """
      <html>
        <body data-asin="$asin">
          <script>
            var imageBlock = {
              "hiRes": "https:\/\/example.invalid\/images\/I\/synthetic-a.jpg",
              "large": "https:\/\/example.invalid\/images\/I\/synthetic-b.jpg",
              "colorImages": {"initial": []},
              "landingImageData": "ImageBlockATF"
            };
          </script>
        </body>
      </html>
    """.trimIndent()
    val result = KindleAmazonCoverClient(StaticHttpClient(html, asin)).lookup(asin)

    assertEquals(CoverLookupStatus.NOT_FOUND, result.lookup.status)
    assertEquals("PRODUCT_PAGE_WITHOUT_COVER", result.traceStep.reason)
    assertEquals("2", result.traceStep.attributes["imagePathMentions"])
    assertEquals("1", result.traceStep.attributes["hiResMentions"])
    assertEquals("1", result.traceStep.attributes["largeImageMentions"])
    assertEquals("1", result.traceStep.attributes["colorImagesMentions"])
    assertEquals("1", result.traceStep.attributes["imageBlockAtfMentions"])
  }

  private class StaticHttpClient(
    private val html: String,
    private val asin: String,
  ) : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse = HttpResponse(
      statusCode = 200,
      reasonPhrase = "OK",
      finalUrl = "https://www.amazon.co.jp/dp/$asin",
      headers = mapOf("Content-Type" to listOf("text/html; charset=UTF-8")),
      body = html.toByteArray(),
    )
  }
}
