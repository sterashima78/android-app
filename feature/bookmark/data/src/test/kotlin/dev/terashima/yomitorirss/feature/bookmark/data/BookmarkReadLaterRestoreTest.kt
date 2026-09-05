package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleGateway
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkReadLaterRestoreTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var repository: DefaultBookmarkRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE bookmarks(" +
            "article_id TEXT PRIMARY KEY," +
            "saved_at TEXT NOT NULL)",
        )
        db.execSQL(
          "CREATE TABLE bookmark_folders(" +
            "id TEXT PRIMARY KEY," +
            "name TEXT NOT NULL," +
            "normalized_name TEXT NOT NULL," +
            "system_kind TEXT," +
            "created_at TEXT NOT NULL)",
        )
        db.execSQL(
          "CREATE TABLE article_folders(" +
            "article_id TEXT PRIMARY KEY," +
            "folder_id TEXT NOT NULL)",
        )
        db.execSQL(
          "CREATE TABLE article_tags(" +
            "article_id TEXT NOT NULL," +
            "tag_id TEXT NOT NULL," +
            "PRIMARY KEY(article_id, tag_id))",
        )
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    repository = DefaultBookmarkRepository(
      database = DatabaseConnection(helper),
      articleRepository = FakeArticleRepository(),
      articleGateway = FakeBookmarkArticleGateway(),
    )
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `あとで読む復元は保存状態とタグを一括で復元する`() = runBlocking {
    repository.restoreReadLater("article-1", setOf("tag-1", "tag-2"))

    assertTrue(repository.isBookmarked("article-1"))
    assertEquals(1, count("article_folders", "article_id='article-1' AND folder_id='$READ_LATER_FOLDER_ID'"))
    assertEquals(2, count("article_tags", "article_id='article-1'"))
  }

  @Test
  fun `タグ復元に失敗した場合は保存状態とあとで読むも残さない`() = runBlocking {
    helper.writableDatabase.execSQL(
      "CREATE TRIGGER reject_tag BEFORE INSERT ON article_tags " +
        "WHEN NEW.tag_id='reject' BEGIN SELECT RAISE(ABORT, 'tag restore failed'); END",
    )

    val result = runCatching {
      repository.restoreReadLater("article-1", setOf("ok", "reject"))
    }

    assertTrue(result.isFailure)
    assertFalse(repository.isBookmarked("article-1"))
    assertEquals(0, count("article_folders", "article_id='article-1'"))
    assertEquals(0, count("article_tags", "article_id='article-1'"))
  }

  private fun count(table: String, where: String): Int =
    helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table WHERE $where", null).use { cursor ->
      check(cursor.moveToFirst())
      cursor.getInt(0)
    }
}

private class FakeArticleRepository : ArticleRepository {
  override val changes: StateFlow<Long> = MutableStateFlow(0L)
  override suspend fun cleanupExpiredArticles() = Unit
  override suspend fun findArticle(articleId: String): Article? = null
  override suspend fun findArticles(articleIds: Collection<String>): List<Article> = emptyList()
  override suspend fun listUnreadArticles(): List<Article> = emptyList()
  override suspend fun listHistoryArticles(): List<Article> = emptyList()
  override suspend fun markArticleRead(articleId: String) = Unit
  override suspend fun markArticleUnread(articleId: String) = Unit
  override suspend fun markAllUnreadAsRead(): Int = 0
  override suspend fun setArticleContentType(articleId: String, contentType: ContentType?) = Unit
}

private class FakeBookmarkArticleGateway : BookmarkArticleGateway {
  override suspend fun markRead(articleId: String) = Unit

  override suspend fun findOrCreateSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): String = error("not used")

  override suspend fun findOrCreateImportedArticle(
    url: String,
    title: String,
    sourceTitle: String,
    createdAt: String,
    identityPrefix: String,
  ): String = error("not used")
}
