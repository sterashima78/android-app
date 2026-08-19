package dev.terashima.yomitorirss.feature.article.data

import android.content.ContentValues
import android.database.Cursor
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentClassificationService
import dev.terashima.yomitorirss.feature.article.ContentClassificationSourceQuery
import dev.terashima.yomitorirss.feature.article.ContentRetentionPolicy
import dev.terashima.yomitorirss.feature.article.ContentRetentionProtectionQuery
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SourceContentTypeOverrides
import dev.terashima.yomitorirss.feature.article.toContentTypeOrNull
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

class DefaultArticleRepository(
  private val database: DatabaseConnection,
  private val contentClassificationSourceQuery: ContentClassificationSourceQuery,
  private val contentRetentionProtectionQuery: ContentRetentionProtectionQuery,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
  private val contentClassificationService: ContentClassificationService = ContentClassificationService(),
  private val contentRetentionPolicy: ContentRetentionPolicy = ContentRetentionPolicy(),
) : ArticleRepository {
  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun cleanupExpiredArticles() {
    val expiredCandidateIds = database.readable.rawQuery(
      "SELECT id FROM articles WHERE saved_at IS NULL AND read_at IS NOT NULL AND read_at<?",
      arrayOf(contentRetentionPolicy.expiryCutoff(Instant.now()).toString()),
    ).use { cursor ->
      buildSet {
        while (cursor.moveToNext()) add(cursor.getString(0))
      }
    }
    if (expiredCandidateIds.isEmpty()) return

    val protectedIds = contentRetentionProtectionQuery.protectedContentIds(expiredCandidateIds)
    val deletableIds = contentRetentionPolicy.deletableContentIds(expiredCandidateIds, protectedIds)
    if (deletableIds.isEmpty()) return

    val deleted = database.transaction {
      deletableIds.chunked(500).sumOf { ids ->
        val placeholders = ids.joinToString(",") { "?" }
        delete("articles", "id IN ($placeholders)", ids.toTypedArray())
      }
    }
    if (deleted > 0) dataChanges.notifyChanged()
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

  private suspend fun articles(sql: String, args: Array<String> = emptyArray()): List<Article> {
    val rows = database.readable.rawQuery(sql, args).use { cursor ->
      buildList { while (cursor.moveToNext()) add(cursor.articleRow()) }
    }
    val sourceIds = rows.mapNotNull(ArticleRow::sourceId).toSet()
    val sourceOverrides = if (sourceIds.isEmpty()) {
      emptyMap()
    } else {
      contentClassificationSourceQuery.findOverrides(sourceIds)
    }
    return rows.map { row ->
      row.article(
        sourceOverrides = row.sourceId?.let(sourceOverrides::get),
        service = contentClassificationService,
      )
    }
  }
}

private const val ARTICLE_SELECT = "SELECT a.* FROM articles a"

private data class ArticleRow(
  val id: String,
  val sourceId: String?,
  val externalId: String?,
  val identityKey: String,
  val url: String,
  val title: String,
  val publishedAt: String,
  val fetchedAt: String,
  val readAt: String?,
  val sourceTitle: String,
  val sourceFeedUrl: String,
  val contentTypeOverride: ContentType?,
)

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}

private fun Cursor.articleRow(): ArticleRow = ArticleRow(
  id = string("id"),
  sourceId = nullableString("feed_id"),
  externalId = nullableString("external_id"),
  identityKey = string("identity_key"),
  url = string("url"),
  title = string("title"),
  publishedAt = string("published_at"),
  fetchedAt = string("fetched_at"),
  readAt = nullableString("read_at"),
  sourceTitle = string("source_title"),
  sourceFeedUrl = string("source_feed_url"),
  contentTypeOverride = nullableString("content_type").toContentTypeOrNull(),
)

private fun ArticleRow.article(
  sourceOverrides: SourceContentTypeOverrides?,
  service: ContentClassificationService,
): Article = Article(
  id = id,
  feedId = sourceId,
  externalId = externalId,
  identityKey = identityKey,
  url = url,
  title = title,
  publishedAt = publishedAt,
  fetchedAt = fetchedAt,
  readAt = readAt,
  sourceTitle = sourceTitle,
  sourceFeedUrl = sourceFeedUrl,
  contentTypeOverride = contentTypeOverride,
  effectiveContentType = service.resolve(contentTypeOverride, sourceOverrides),
)

private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))

private fun Cursor.nullableString(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }

private fun nowIso(): String = Instant.now().toString()
