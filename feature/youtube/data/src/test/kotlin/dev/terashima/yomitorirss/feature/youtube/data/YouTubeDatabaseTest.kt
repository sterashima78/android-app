package dev.terashima.yomitorirss.feature.youtube.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
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
class YouTubeDatabaseTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var database: YouTubeDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
      }

      override fun onCreate(db: SQLiteDatabase) {
        youtubeDatabaseSchema.createSchema(db)
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    database = YouTubeDatabase(DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `同じvideo idを再取得しても既読状態を維持する`() {
    val first = feed(title = "First title")
    database.upsertFeed(first)
    database.markRead("video-1")

    database.upsertFeed(feed(title = "Updated title"))

    assertTrue(database.listUnreadVideos().isEmpty())
    val history = database.listHistoryVideos()
    assertEquals(1, history.size)
    assertEquals("video-1", history.single().id)
    assertEquals("Updated title", history.single().title)
    assertTrue(history.single().isRead)
  }

  private fun feed(title: String) = ParsedYouTubeFeed(
    channelId = "UC_x5XG1OV2P6uZZ5FSM9Ttw",
    channelTitle = "Channel",
    videos = listOf(
      ParsedYouTubeVideo(
        id = "video-1",
        channelId = "UC_x5XG1OV2P6uZZ5FSM9Ttw",
        title = title,
        url = "https://www.youtube.com/watch?v=video-1",
        publishedAtEpochMillis = 1_000L,
      ),
    ),
  )
}
