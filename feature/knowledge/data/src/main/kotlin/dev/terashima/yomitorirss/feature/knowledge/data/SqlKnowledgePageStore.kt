package dev.terashima.yomitorirss.feature.knowledge.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePage
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageSummary
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeSource
import kotlinx.coroutines.flow.StateFlow

class SqlKnowledgePageStore(
  private val database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier.shared,
) {
  val changes: StateFlow<Long> = dataChanges.version

  fun listPages(query: String): List<KnowledgePageSummary> {
    val normalized = query.trim()
    val pattern = "%$normalized%"
    return database.readable.rawQuery(
      "SELECT id,title,source_count,generated_at,editor_managed FROM knowledge_pages " +
        "WHERE (? = '' OR title LIKE ? OR body_markdown LIKE ?) " +
        "ORDER BY editor_managed DESC,generated_at DESC,title COLLATE NOCASE",
      arrayOf(normalized, pattern, pattern),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            KnowledgePageSummary(
              id = cursor.getString(0),
              title = cursor.getString(1),
              sourceCount = cursor.getInt(2),
              generatedAt = cursor.getString(3),
              editorManaged = cursor.getInt(4) != 0,
            ),
          )
        }
      }
    }
  }

  fun findPage(id: String): KnowledgePage? {
    val header = loadPageHeader(id) ?: return null
    val sources = database.readable.rawQuery(
      "SELECT citation_index,article_id,title,url,source_title,saved_at FROM knowledge_page_sources " +
        "WHERE page_id = ? ORDER BY citation_index",
      arrayOf(id),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            KnowledgeSource(
              citationNumber = cursor.getInt(0),
              articleId = cursor.getString(1),
              title = cursor.getString(2),
              url = cursor.getString(3),
              sourceTitle = cursor.getString(4),
              savedAt = cursor.getString(5),
            ),
          )
        }
      }
    }

    return KnowledgePage(
      id = header.id,
      title = header.title,
      bodyMarkdown = header.bodyMarkdown,
      sourceCount = header.sourceCount,
      generatedAt = header.generatedAt,
      editorManaged = header.editorManaged,
      sources = sources,
    )
  }

  internal fun loadPageHeader(id: String): KnowledgePageHeader? = database.readable.rawQuery(
    "SELECT id,title,body_markdown,topic_kind,topic_key,source_count,generated_at,editor_managed " +
      "FROM knowledge_pages WHERE id = ?",
    arrayOf(id),
  ).use { cursor ->
    if (!cursor.moveToFirst()) return@use null
    KnowledgePageHeader(
      id = cursor.getString(0),
      title = cursor.getString(1),
      bodyMarkdown = cursor.getString(2),
      topicKind = cursor.getString(3),
      topicKey = cursor.getString(4),
      sourceCount = cursor.getInt(5),
      generatedAt = cursor.getString(6),
      editorManaged = cursor.getInt(7) != 0,
    )
  }

  internal fun loadFingerprints(): Map<String, String> = database.readable.rawQuery(
    "SELECT id,source_fingerprint FROM knowledge_pages WHERE editor_managed = 0",
    emptyArray(),
  ).use { cursor ->
    buildMap {
      while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
    }
  }

  internal fun loadEditorManagedIds(): Set<String> = database.readable.rawQuery(
    "SELECT id FROM knowledge_pages WHERE editor_managed = 1",
    emptyArray(),
  ).use { cursor ->
    buildSet {
      while (cursor.moveToNext()) add(cursor.getString(0))
    }
  }

  internal fun deleteObsoletePages(activeIds: Set<String>) {
    database.transaction {
      if (activeIds.isEmpty()) {
        delete("knowledge_pages", "editor_managed = 0", null)
      } else {
        val placeholders = activeIds.joinToString(",") { "?" }
        delete(
          "knowledge_pages",
          "editor_managed = 0 AND id NOT IN ($placeholders)",
          activeIds.toTypedArray(),
        )
      }
    }
  }

  internal fun persistPage(
    id: String,
    title: String,
    body: String,
    topicKind: String,
    topicKey: String,
    editorManaged: Boolean,
    sources: List<KnowledgeGenerationSource>,
    sourceFingerprint: String,
    generatedAt: String,
  ) {
    database.transaction {
      val values = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("body_markdown", body)
        put("topic_kind", topicKind)
        put("topic_key", topicKey)
        put("source_count", sources.size)
        put("source_fingerprint", sourceFingerprint)
        put("generated_at", generatedAt)
        put("editor_managed", if (editorManaged) 1 else 0)
      }
      val updated = update("knowledge_pages", values, "id = ?", arrayOf(id))
      if (updated == 0) insertOrThrow("knowledge_pages", null, values)

      delete("knowledge_page_sources", "page_id = ?", arrayOf(id))
      sources.forEachIndexed { index, source ->
        insertOrThrow(
          "knowledge_page_sources",
          null,
          ContentValues().apply {
            put("page_id", id)
            put("article_id", source.articleId)
            put("citation_index", index + 1)
            put("title", source.title)
            put("url", source.url)
            put("source_title", source.sourceTitle)
            put("saved_at", source.savedAt)
          },
        )
      }
    }
  }

  internal fun notifyChanged() {
    dataChanges.notifyChanged()
  }
}

internal data class KnowledgePageHeader(
  val id: String,
  val title: String,
  val bodyMarkdown: String,
  val topicKind: String,
  val topicKey: String,
  val sourceCount: Int,
  val generatedAt: String,
  val editorManaged: Boolean,
)
