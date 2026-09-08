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
  fun `一つのsubscription更新失敗は他を更新したあと部分失敗として通知する`() = runBlocking {
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

  private class PartialFailureHttpClient : HttpClient {
    val requestedChannelIds = mutableListOf<String>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
      val channelId = request.channelId()
      requestedChannelIds += channelId
      return if (channelId == CHANNEL_ID_1) {
        HttpResponse(
          statusCode = 500,
          reasonPhrase = "failure",
          finalUrl = request.url,
          headers = emptyMap(),
          body = byteArrayOf(),
        )
      } else {
        successResponse(channelId, videoId = "video-2")
      }
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
