package dev.terashima.yomitorirss.feature.article.data

import android.content.ContentValues
import android.database.Cursor
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.resolveContentType
import dev.terashima.yomitorirss.feature.article.toContentTypeOrNull
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

class DefaultArticleRepository(
  private val database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
) : ArticleRepository {
  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun cleanupExpiredArticles() {
    database.writable.delete(
      "articles",
      "saved_at IS NULL AND read_at IS NOT NULL AND read_at<? AND NOT EXISTS(SELECT 1 FROM article_summaries s WHERE s.article_id=articles.id) AND NOT EXISTS(SELECT 1 FROM summary_tasks q WHERE q.article_id=articles.id AND q.state IN('queued','running'))",
      arrayOf(Instant.now().minusSeconds(30L * 86400).toString()),
    )
    dataChanges.notifyChanged()
  }

  override suspend fun listUnreadArticles(): List<Article> = articles(
    "$ARTICLE_SELECT WHERE a.read_at IS NULL ORDER BY a.published_at DESC LIMIT 500",
  )

  override suspend fun listHistoryArticles(): List<Article> = articles(
    "$ARTICLE_SELECT WHERE a.read_at>=? ORDER BY a.read_at DESC LIMIT 500",
    arrayOf(Instant.now().minusSeconds(30L * 86400).toString()),
  )

  override suspend fun markArticleRead(articleId: String) {
    updateArticle(articleId, "read_at", nowIso())
    dataChanges.notifyChanged()
  }

  override suspend fun markArticleUnread(articleId: String) {
    updateArticle(articleId, "read_at", null)
    dataChanges.notifyChanged()
  }

  override suspend fun markAllUnreadAsRead(): Int {
    val count = database.writable.update("articles", values("read_at" to nowIso()), "read_at IS NULL", null)
    if (count > 0) dataChanges.notifyChanged()
    return count
  }

  override suspend fun setArticleContentType(articleId: String, contentType: ContentType?) {
    updateArticle(articleId, "content_type", contentType?.name)
    dataChanges.notifyChanged()
  }

  private fun updateArticle(id: String, column: String, value: String?) {
    database.writable.update("articles", values(column to value), "id=?", arrayOf(id))
  }

  private fun articles(sql: String, args: Array<String> = emptyArray()): List<Article> =
    database.readable.rawQuery(sql, args).use { cursor ->
      buildList { while (cursor.moveToNext()) add(cursor.article()) }
    }
}

private const val ARTICLE_SELECT = """
  SELECT a.*, f.content_type AS feed_content_type, ff.content_type AS folder_content_type
  FROM articles a
  LEFT JOIN feeds f ON f.id = a.feed_id
  LEFT JOIN feed_folders ff ON ff.id = f.folder_id
"""

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}

private fun Cursor.article(): Article {
  val articleOverride = nullableString("content_type").toContentTypeOrNull()
  val feedOverride = nullableString("feed_content_type").toContentTypeOrNull()
  val folderOverride = nullableString("folder_content_type").toContentTypeOrNull()
  val inheritedType = resolveContentType(null, feedOverride, folderOverride)
  return Article(
    id = string("id"),
    feedId = nullableString("feed_id"),
    externalId = nullableString("external_id"),
    identityKey = string("identity_key"),
    url = string("url"),
    title = string("title"),
    publishedAt = string("published_at"),
    fetchedAt = string("fetched_at"),
    readAt = nullableString("read_at"),
    sourceTitle = string("source_title"),
    sourceFeedUrl = string("source_feed_url"),
    contentTypeOverride = articleOverride,
    inheritedContentType = inheritedType,
    effectiveContentType = resolveContentType(articleOverride, feedOverride, folderOverride),
  )
}

private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))

private fun Cursor.nullableString(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }

private fun nowIso(): String = Instant.now().toString()
