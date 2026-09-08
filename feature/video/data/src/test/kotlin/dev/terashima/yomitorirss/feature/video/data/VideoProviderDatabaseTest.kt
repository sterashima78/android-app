package dev.terashima.yomitorirss.feature.video.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderType
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
class VideoProviderDatabaseTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var database: VideoProviderDatabase

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
    database = VideoProviderDatabase(DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `新しく取得した購読動画は未読として追加する`() {
    val provider = database.saveProvider(testProvider())

    val (subscription, added) = database.upsertProviderFeed(provider, testFeed())

    assertEquals(1, added)
    assertEquals("channel-1", subscription.sourceId)
    val unread = database.unreadVideos().single()
    assertEquals("video-1", unread.providerItemId)
    assertFalse(unread.isRead)
    assertEquals(subscription.id, unread.subscriptionId)
  }

  @Test
  fun `再取得しても既存動画の既読状態を維持する`() {
    val provider = database.saveProvider(testProvider())
    database.upsertProviderFeed(provider, testFeed())
    val video = database.unreadVideos().single()
    database.markRead(video.video.id)

    val (_, added) = database.upsertProviderFeed(
      provider,
      testFeed(title = "更新されたタイトル"),
    )

    assertEquals(0, added)
    assertTrue(database.unreadVideos().isEmpty())
    val history = database.historyVideos(10).single()
    assertTrue(history.isRead)
    assertEquals("更新されたタイトル", history.video.title)
  }

  @Test
  fun `provider設定を更新してもsubscriptionとitemを維持する`() {
    val provider = database.saveProvider(testProvider())
    val (subscription, _) = database.upsertProviderFeed(provider, testFeed())

    database.saveProvider(provider.copy(enabled = false, name = "Disabled Provider"))

    assertFalse(database.providers().single().enabled)
    assertEquals(subscription.id, database.subscriptions(provider.id).single().id)
    assertEquals(subscription.id, database.unreadVideos().single().subscriptionId)
  }

  @Test
  fun `feed再取得で返らない既存itemもsubscription membershipを維持する`() {
    val provider = database.saveProvider(testProvider())
    val (subscription, _) = database.upsertProviderFeed(
      provider,
      testFeed(
        videos = listOf(
          testFeedItem("video-1", 1_000L),
          testFeedItem("video-2", 2_000L),
        ),
      ),
    )

    database.upsertProviderFeed(
      provider,
      testFeed(videos = listOf(testFeedItem("video-2", 2_000L))),
    )

    val unread = database.unreadVideos()
    assertEquals(setOf("video-1", "video-2"), unread.map { it.providerItemId }.toSet())
    assertTrue(unread.all { it.subscriptionId == subscription.id })
  }

  @Test
  fun `購読解除は未保存かつ未再生の動画をcatalogから削除する`() {
    val provider = database.saveProvider(testProvider())
    val (subscription, _) = database.upsertProviderFeed(provider, testFeed())
    val videoId = database.unreadVideos().single().video.id

    database.unsubscribe(subscription.id)

    assertTrue(database.subscriptions(provider.id).isEmpty())
    assertFalse(videoExists(videoId))
  }

  @Test
  fun `購読解除しても保存済み動画はcatalogに残すがprovider一覧から外す`() {
    val provider = database.saveProvider(testProvider())
    val (subscription, _) = database.upsertProviderFeed(provider, testFeed())
    val videoId = database.unreadVideos().single().video.id
    helper.writableDatabase.insertOrThrow(
      "video_saved_items",
      null,
      ContentValues().apply {
        put("video_id", videoId)
        putNull("folder_id")
        put("saved_at", 100L)
      },
    )

    database.unsubscribe(subscription.id)

    assertTrue(videoExists(videoId))
    assertFalse(providerItemExists(videoId))
    assertTrue(database.unreadVideos().isEmpty())
    assertTrue(database.historyVideos(10).isEmpty())
  }

  private fun videoExists(videoId: String): Boolean = helper.readableDatabase.rawQuery(
    "SELECT 1 FROM video_items WHERE id = ? LIMIT 1",
    arrayOf(videoId),
  ).use { it.moveToFirst() }

  private fun providerItemExists(videoId: String): Boolean = helper.readableDatabase.rawQuery(
    "SELECT 1 FROM video_provider_items WHERE video_id = ? LIMIT 1",
    arrayOf(videoId),
  ).use { it.moveToFirst() }

  private fun testProvider(): VideoProvider = VideoProvider(
    id = "provider-1",
    type = VideoProviderType.YOUTUBE,
    name = "Provider",
    enabled = true,
  )

  private fun testFeed(
    title: String = "動画1",
    videos: List<VideoProviderFeedItem> = listOf(
      VideoProviderFeedItem(
        id = "video-1",
        title = title,
        url = "https://example.invalid/video-1",
        thumbnailUrl = null,
        publishedAtEpochMillis = 1_000L,
      ),
    ),
  ): VideoProviderFeed = VideoProviderFeed(
    sourceId = "channel-1",
    title = "チャンネル1",
    sourceUrl = "https://example.invalid/channel-1",
    videos = videos,
  )

  private fun testFeedItem(id: String, publishedAt: Long): VideoProviderFeedItem = VideoProviderFeedItem(
    id = id,
    title = id,
    url = "https://example.invalid/$id",
    thumbnailUrl = null,
    publishedAtEpochMillis = publishedAt,
  )
}
