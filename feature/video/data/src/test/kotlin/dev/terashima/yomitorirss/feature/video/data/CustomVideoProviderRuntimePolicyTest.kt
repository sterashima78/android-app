package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.core.network.HttpMethod
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomVideoProviderRuntimePolicyTest {
  @Test
  fun `custom provider http requestはboundedなHTTPS requestへ変換する`() {
    val request = AndroidCustomVideoProviderRuntime.buildHttpRequest(
      JSONObject()
        .put("url", "https://example.invalid/feed")
        .put("method", "POST")
        .put("headers", JSONObject().put("Accept", "application/json"))
        .put("body", "request-body")
        .put("contentType", "application/json"),
    )

    assertEquals("https://example.invalid/feed", request.url)
    assertEquals(HttpMethod.POST, request.method)
    assertEquals("application/json", request.headers["Accept"])
    assertEquals("request-body", request.body?.toString(Charsets.UTF_8))
    assertEquals("application/json", request.contentType)
    assertEquals(4L * 1024 * 1024, request.maxResponseBytes)
    assertEquals(request.maxResponseBytes, request.maxErrorResponseBytes)
  }

  @Test
  fun `custom provider http requestはHTTPS以外を拒否する`() {
    val error = runCatching {
      AndroidCustomVideoProviderRuntime.buildHttpRequest(
        JSONObject().put("url", "http://example.invalid/feed"),
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `custom provider http requestはcredential headerを拒否する`() {
    val error = runCatching {
      AndroidCustomVideoProviderRuntime.buildHttpRequest(
        JSONObject()
          .put("url", "https://example.invalid/feed")
          .put("headers", JSONObject().put("Authorization", "secret")),
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }

  @Test
  fun `custom provider feedを共通provider itemへ変換する`() {
    val feed = AndroidCustomVideoProviderRuntime.parseFeedValue(
      JSONObject()
        .put("sourceId", "source-1")
        .put("title", "Source")
        .put("sourceUrl", "https://example.invalid/source")
        .put(
          "videos",
          JSONArray().put(
            JSONObject()
              .put("id", "video-1")
              .put("title", "Video")
              .put("url", "https://example.invalid/video")
              .put("publishedAtEpochMillis", 1234L),
          ),
        ),
    )

    assertEquals("source-1", feed.sourceId)
    assertEquals("Source", feed.title)
    assertEquals("https://example.invalid/source", feed.sourceUrl)
    assertEquals(1, feed.videos.size)
    assertEquals("video-1", feed.videos.single().id)
    assertNull(feed.videos.single().thumbnailUrl)
    assertEquals(1234L, feed.videos.single().publishedAtEpochMillis)
  }

  @Test
  fun `custom provider feedはcredentialを含むURLを拒否する`() {
    val error = runCatching {
      AndroidCustomVideoProviderRuntime.parseFeedValue(
        JSONObject()
          .put("sourceId", "source-1")
          .put("title", "Source")
          .put("sourceUrl", "https://user:secret@example.invalid/source")
          .put("videos", JSONArray()),
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
  }
}
