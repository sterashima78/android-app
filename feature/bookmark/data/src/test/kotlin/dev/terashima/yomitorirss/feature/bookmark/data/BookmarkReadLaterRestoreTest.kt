package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.ContentValues
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
import dev.terashima.yomitorirss.feature.bookmark.Tag
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
      override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
      }

      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE bookmarks(" +
            "article_id TEXT PRIMARY KEY," +
            "saved_at TEXT NOT NULL)",
        )
        db.execSQL(
          "CREATE TABLE tags(" +
            "id TEXT PRIMARY KEY NOT NULL," +
            "name TEXT NOT NULL," +
            "normalized_name TEXT NOT NULL UNIQUE," +
            "created_at TEXT NOT NULL)",
        )
        db.execSQL(
          "CREATE TABLE bookmark_folders(" +
            "id TEXT PRIMARY KEY," +
            "name TEXT NOT NULL," +
            "normalized_name TEXT NOT NULL UNIQUE," +
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
            "tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE," +
            "PRIMARY KEY(article_id, tag_id))",
        )
        db.execSQL(
          "CREATE TRIGGER cleanup_unused_tags_after_article_tag_delete " +
            "AFTER DELETE ON article_tags " +
            "WHEN NOT EXISTS(SELECT 1 FROM article_tags WHERE tag_id=OLD.tag_id) " +
            "BEGIN DELETE FROM tags WHERE id=OLD.tag_id; END",
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
  fun `削除時に消えた専用タグもあとで読むUndoで復元する`() = runBlocking {
    val snapshot = tag("tag-1", "Solo")
    insertTag(snapshot)
    repository.markReadLater("article-1")
    repository.replaceArticleTags("article-1", setOf(snapshot.id))

    repository.unsaveArticle("article-1")

    assertFalse(repository.isBookmarked("article-1"))
    assertEquals(0, count("tags", "id='${snapshot.id}'"))

    repository.restoreReadLater("article-1", setOf(snapshot))

    assertTrue(repository.isBookmarked("article-1"))
    assertEquals(1, count("tags", "id='${snapshot.id}'"))
    assertEquals(1, count("article_folders", "article_id='article-1' AND folder_id='$READ_LATER_FOLDER_ID'"))
    assertEquals(1, count("article_tags", "article_id='article-1' AND tag_id='${snapshot.id}'"))
  }

  @Test
  fun `同名タグが再作成済みなら現在のタグを再利用する`() = runBlocking {
    val snapshot = tag("old-tag", "Solo")
    val current = tag("new-tag", "Solo")
    insertTag(current)

    repository.restoreReadLater("article-1", setOf(snapshot))

    assertEquals(0, count("tags", "id='${snapshot.id}'"))
    assertEquals(1, count("tags", "id='${current.id}'"))
    assertEquals(1, count("article_tags", "article_id='article-1' AND tag_id='${current.id}'"))
  }

  @Test
  fun `タグ関連付け復元に失敗した場合はタグと保存状態とあとで読むを残さない`() = runBlocking {
    helper.writableDatabase.execSQL(
      "CREATE TRIGGER reject_tag BEFORE INSERT ON article_tags " +
        "WHEN NEW.tag_id='reject' BEGIN SELECT RAISE(ABORT, 'tag restore failed'); END",
    )
    val tags = setOf(tag("ok", "Ok"), tag("reject", "Reject"))

    val result = runCatching {
      repository.restoreReadLater("article-1", tags)
    }

    assertTrue(result.isFailure)
    assertFalse(repository.isBookmarked("article-1"))
    assertEquals(0, count("tags", "1=1"))
    assertEquals(0, count("article_folders", "article_id='article-1'"))
    assertEquals(0, count("article_tags", "article_id='article-1'"))
  }

  private fun insertTag(tag: Tag) {
    helper.writableDatabase.insertOrThrow(
      "tags",
      null,
      ContentValues().apply {
        put("id", tag.id)
        put("name", tag.name)
        put("normalized_name", tag.normalizedName)
        put("created_at", tag.createdAt)
      },
    )
  }

  private fun tag(id: String, name: String) = Tag(
    id = id,
    name = name,
    normalizedName = name.lowercase(),
    createdAt = "2026-09-06T00:00:00Z",
  )

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
