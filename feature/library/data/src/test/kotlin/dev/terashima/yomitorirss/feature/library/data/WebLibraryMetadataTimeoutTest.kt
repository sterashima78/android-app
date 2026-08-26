package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.library.MAX_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.library.MIN_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLibraryMetadataTimeoutTest {
  @Test
  fun `一致する取得ルールのタイムアウトをWebView全体へ適用する`() {
    val extractor = WebLibraryMetadataExtractor(
      id = "rule-1",
      urlPattern = "https://example.com/books/*",
      functionCode = "async () => ({ title: null, thumbnailUrl: null })",
      timeoutSeconds = 45,
      updatedAt = 1L,
    )

    assertEquals(
      45_000L,
      webLibraryMetadataTimeoutMillis(
        extractors = listOf(extractor),
        requestedUrl = "https://example.com/books/1",
        fallbackTimeoutMillis = 15_000L,
      ),
    )
  }

  @Test
  fun `取得ルールが一致しなければ既定のWebViewタイムアウトを使う`() {
    val extractor = WebLibraryMetadataExtractor(
      id = "rule-1",
      urlPattern = "https://example.com/books/*",
      functionCode = "async () => ({ title: null, thumbnailUrl: null })",
      timeoutSeconds = 45,
      updatedAt = 1L,
    )

    assertEquals(
      15_000L,
      webLibraryMetadataTimeoutMillis(
        extractors = listOf(extractor),
        requestedUrl = "https://example.com/articles/1",
        fallbackTimeoutMillis = 15_000L,
      ),
    )
  }

  @Test
  fun `取得ルールのタイムアウトは許容範囲外を拒否する`() {
    val functionCode = "async () => ({ title: null, thumbnailUrl: null })"

    val tooShort = runCatching {
      validateWebLibraryMetadataExtractor(
        urlPattern = "https://example.com/*",
        functionCode = functionCode,
        timeoutSeconds = MIN_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS - 1,
      )
    }.exceptionOrNull()
    val tooLong = runCatching {
      validateWebLibraryMetadataExtractor(
        urlPattern = "https://example.com/*",
        functionCode = functionCode,
        timeoutSeconds = MAX_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS + 1,
      )
    }.exceptionOrNull()

    assertTrue(tooShort is IllegalArgumentException)
    assertTrue(tooLong is IllegalArgumentException)
  }

  @Test
  fun `取得ルールの既定タイムアウトは15秒`() {
    val extractor = WebLibraryMetadataExtractor(
      id = "rule-1",
      urlPattern = "https://example.com/*",
      functionCode = "async () => ({ title: null, thumbnailUrl: null })",
      updatedAt = 1L,
    )

    assertEquals(DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS, extractor.timeoutSeconds)
  }
}
