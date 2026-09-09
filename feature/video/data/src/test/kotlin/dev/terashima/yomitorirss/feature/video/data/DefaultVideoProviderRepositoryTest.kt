package dev.terashima.yomitorirss.feature.video.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultVideoProviderRepositoryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var connection: DatabaseConnection

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
      }

      override fun onCreate(db: SQLiteDatabase) = ensureVideoSchema(db)
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    connection = DatabaseConnection(helper)
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `provider refreshはcoroutine cancellationを失敗件数へ変換しない`() = runBlocking {
    val database = VideoProviderDatabase(connection)
    val provider = database.saveProvider(testProvider())
    database.upsertProviderFeed(provider, emptyFeed(CHANNEL_ID_1, "Channel A"))
    val repository = DefaultVideoProviderRepository(connection, CancellingHttpClient)

    val error = runCatching { repository.refreshProviders() }.exceptionOrNull()

    assertTrue(error is CancellationException)
  }

  @Test
  fun `一つのsubscription更新失敗は他を更新したあと理由付き部分失敗として通知する`() = runBlocking {
    val database = VideoProviderDatabase(connection)
    val provider = database.saveProvider(testProvider())
    database.upsertProviderFeed(provider, emptyFeed(CHANNEL_ID_1, "Channel A"))
    database.upsertProviderFeed(provider, emptyFeed(CHANNEL_ID_2, "Channel B"))
    val http = PartialFailureHttpClient()
    val repository = DefaultVideoProviderRepository(connection, http)

    val error = runCatching { repository.refreshProviders() }.exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals(setOf(CHANNEL_ID_1, CHANNEL_ID_2), http.requestedChannelIds.toSet())
    assertEquals("video-2", repository.unreadVideos().single().providerItemId)
    assertEquals(
      "動画プロバイダの一部を更新できませんでした（成功: 1 / 失敗: 1）\n失敗理由: HTTP 400 × 1",
      error?.message,
    )
    assertEquals(1, error?.suppressed?.size)
  }

  @Test
  fun `一時的な404は再試行し成功できる`() = runBlocking {
    val database = VideoProviderDatabase(connection)
    val provider = database.saveProvider(testProvider())
    database.upsertProviderFeed(provider, emptyFeed(CHANNEL_ID_1, "Channel A"))
    val http = TransientNotFoundHttpClient()
    val repository = DefaultVideoProviderRepository(connection, http)

    val result = repository.refreshProviders()

    assertEquals(2, http.requestCount)
    assertEquals(1, result.refreshedSubscriptions)
    assertEquals(0, result.failedSubscriptions)
    assertEquals(1, result.addedVideos)
    assertEquals("video-retried", repository.unreadVideos().single().providerItemId)
  }

  @Test
  fun `複数の最終失敗はHTTP statusごとに集約する`() = runBlocking {
    val database = VideoProviderDatabase(connection)
    val provider = database.saveProvider(testProvider())
    database.upsertProviderFeed(provider, emptyFeed(CHANNEL_ID_1, "Channel A"))
    database.upsertProviderFeed(provider, emptyFeed(CHANNEL_ID_2, "Channel B"))
    val repository = DefaultVideoProviderRepository(connection, DistinctPermanentFailureHttpClient())

    val error = runCatching { repository.refreshProviders() }.exceptionOrNull()

    assertTrue(error is IOException)
    assertEquals(
      "動画プロバイダの一部を更新できませんでした（成功: 0 / 失敗: 2）\n" +
        "失敗理由: HTTP 400 × 1 / HTTP 403 × 1",
      error?.message,
    )
    assertEquals(2, error?.suppressed?.size)
  }

  @Test
  fun `custom providerの失敗は自動再試行対象にしない`() {
    assertFalse(shouldRetryProviderRefresh(VideoProviderType.CUSTOM, IOException("network")))
    assertTrue(shouldRetryProviderRefresh(VideoProviderType.YOUTUBE, VideoProviderHttpException(500)))
  }

  @Test
  fun `無効なproviderはrefresh対象にしない`() = runBlocking {
    val database = VideoProviderDatabase(connection)
    val provider = database.saveProvider(testProvider())
    database.upsertProviderFeed(provider, emptyFeed(CHANNEL_ID_1, "Channel A"))
    database.saveProvider(provider.copy(enabled = false))
    val http = RecordingSuccessHttpClient()
    val repository = DefaultVideoProviderRepository(connection, http)

    val result = repository.refreshProviders()

    assertEquals(0, result.refreshedSubscriptions)
    assertEquals(0, result.failedSubscriptions)
    assertEquals(0, result.addedVideos)
    assertTrue(http.requests.isEmpty())
  }

  @Test
  fun `custom providerは共通subscriptionとrefresh lifecycleを利用する`() = runBlocking {
    val runtime = RecordingCustomProviderRuntime()
    val repository = DefaultVideoProviderRepository(connection, UnusedHttpClient, runtime)
    val provider = repository.saveProvider(
      VideoProvider(
        id = "",
        type = VideoProviderType.CUSTOM,
        name = "Custom Provider",
        functionCode = "async (input, api) => ({})",
      ),
    )

    val subscription = repository.subscribe(provider.id, "source-input")
    val refresh = repository.refreshProviders(provider.id)

    assertEquals("custom-source", subscription.sourceId)
    assertEquals(listOf("source-input"), runtime.subscribeInputs)
    assertEquals(listOf("custom-source"), runtime.refreshInputs)
    assertEquals(listOf(provider.functionCode, provider.functionCode), runtime.functionCodes)
    assertEquals(1, refresh.refreshedSubscriptions)
    assertEquals(1, refresh.addedVideos)
    assertEquals(setOf("custom-video-1", "custom-video-2"), repository.unreadVideos().map { it.providerItemId }.toSet())
  }

  private fun testProvider(): VideoProvider = VideoProvider(
    id = "provider-1",
    type = VideoProviderType.YOUTUBE,
    name = "Provider",
  )

  private fun emptyFeed(channelId: String, title: String): VideoProviderFeed = VideoProviderFeed(
    sourceId = channelId,
    title = title,
    sourceUrl = "https://example.invalid/$channelId",
    videos = emptyList(),
  )

  private object CancellingHttpClient : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse {
      throw CancellationException("cancelled")
    }
  }

  private object UnusedHttpClient : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse = error("HTTP client should not be used")
  }

  private class RecordingCustomProviderRuntime : CustomVideoProviderRuntime {
    val subscribeInputs = mutableListOf<String>()
    val refreshInputs = mutableListOf<String>()
    val functionCodes = mutableListOf<String>()

    override suspend fun subscribe(functionCode: String, sourceUrl: String): VideoProviderFeed {
      functionCodes += functionCode
      subscribeInputs += sourceUrl
      return customFeed("custom-video-1")
    }

    override suspend fun refresh(functionCode: String, sourceId: String): VideoProviderFeed {
      functionCodes += functionCode
      refreshInputs += sourceId
      return customFeed("custom-video-2")
    }

    private fun customFeed(videoId: String): VideoProviderFeed = VideoProviderFeed(
      sourceId = "custom-source",
      title = "Custom Source",
      sourceUrl = "https://example.invalid/custom-source",
      videos = listOf(
        VideoProviderFeedItem(
          id = videoId,
          title = videoId,
          url = "https://example.invalid/$videoId",
          thumbnailUrl = null,
          publishedAtEpochMillis = if (videoId.endsWith("1")) 1_000L else 2_000L,
        ),
      ),
    )
  }

  private class PartialFailureHttpClient : HttpClient {
    val requestedChannelIds = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
      val channelId = request.channelId()
      requestedChannelIds += channelId
      return if (channelId == CHANNEL_ID_1) {
        failureResponse(request, 400)
      } else {
        successResponse(channelId, videoId = "video-2")
      }
    }
  }

  private class TransientNotFoundHttpClient : HttpClient {
    var requestCount = 0

    override suspend fun execute(request: HttpRequest): HttpResponse {
      requestCount += 1
      return if (requestCount == 1) {
        failureResponse(request, 404)
      } else {
        successResponse(request.channelId(), videoId = "video-retried")
      }
    }
  }

  private class DistinctPermanentFailureHttpClient : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse {
      val status = if (request.channelId() == CHANNEL_ID_1) 400 else 403
      return failureResponse(request, status)
    }
  }

  private class RecordingSuccessHttpClient : HttpClient {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
      requests += request
      return successResponse(request.channelId(), videoId = "video")
    }
  }

  private companion object {
    const val CHANNEL_ID_1 = "UCabcdefghijklmnopqrstuv"
    const val CHANNEL_ID_2 = "UCbcdefghijklmnopqrstuvw"

    fun HttpRequest.channelId(): String {
      val playlistId = url.substringAfter("playlist_id=UULF")
      return "UC$playlistId"
    }

    fun failureResponse(request: HttpRequest, statusCode: Int): HttpResponse = HttpResponse(
      statusCode = statusCode,
      reasonPhrase = "failure",
      finalUrl = request.url,
      headers = emptyMap(),
      body = byteArrayOf(),
    )

    fun successResponse(channelId: String, videoId: String): HttpResponse {
      val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015">
          <yt:channelId>$channelId</yt:channelId>
          <title>Channel</title>
          <entry>
            <yt:videoId>$videoId</yt:videoId>
            <yt:channelId>$channelId</yt:channelId>
            <title>Video</title>
            <link rel="alternate" href="https://example.invalid/$videoId" />
            <published>2026-01-01T00:00:00Z</published>
          </entry>
        </feed>
      """.trimIndent()
      return HttpResponse(
        statusCode = 200,
        reasonPhrase = "OK",
        finalUrl = "https://example.invalid/feed",
        headers = emptyMap(),
        body = xml.toByteArray(),
      )
    }
  }
}
