package dev.terashima.yomitorirss.feature.article.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkArticleGatewayTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var gateway: DefaultBookmarkArticleGateway

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          """
            CREATE TABLE articles(
              id TEXT PRIMARY KEY,
              feed_id TEXT,
              external_id TEXT,
              identity_key TEXT,
              url TEXT,
              title TEXT,
              published_at TEXT,
              fetched_at TEXT,
              read_at TEXT,
              saved_at TEXT,
              source_title TEXT,
              source_feed_url TEXT
            )
          """.trimIndent(),
        )
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    gateway = DefaultBookmarkArticleGateway(DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `共有URLの初回保存はContentを作成して保存済みにする`() = runBlocking {
    val saved = gateway.saveSharedArticle(
      url = "https://example.com/article",
      title = "Example",
      sourceTitle = "Shared",
    )

    assertEquals(BookmarkSaveResult.ADDED, saved.result)
    helper.readableDatabase.rawQuery(
      "SELECT read_at,saved_at FROM articles WHERE id=?",
      arrayOf(saved.articleId),
    ).use { cursor ->
      assertEquals(true, cursor.moveToFirst())
      assertNotNull(cursor.getString(0))
      assertNotNull(cursor.getString(1))
    }
  }

  @Test
  fun `同じ共有URLを再保存してもContentを重複作成しない`() = runBlocking {
    val first = gateway.saveSharedArticle("https://example.com/article", "Example", "Shared")
    val second = gateway.saveSharedArticle("https://example.com/article", "Example", "Shared")

    assertEquals(first.articleId, second.articleId)
    assertEquals(BookmarkSaveResult.ALREADY_BOOKMARKED, second.result)
    helper.readableDatabase.rawQuery(
      "SELECT COUNT(*) FROM articles WHERE url=?",
      arrayOf("https://example.com/article"),
    ).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, cursor.getInt(0))
    }
  }

  @Test
  fun `保存解除では既読状態を残してbookmark状態だけを外す`() = runBlocking {
    val saved = gateway.saveSharedArticle("https://example.com/article", "Example", "Shared")

    gateway.unsave(saved.articleId)

    assertFalse(gateway.isBookmarked(saved.articleId))
    helper.readableDatabase.rawQuery(
      "SELECT read_at,saved_at FROM articles WHERE id=?",
      arrayOf(saved.articleId),
    ).use { cursor ->
      cursor.moveToFirst()
      assertNotNull(cursor.getString(0))
      assertEquals(true, cursor.isNull(1))
    }
  }
}
