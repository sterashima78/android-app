package dev.terashima.yomitorirss.feature.knowledge.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePage
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageSummary
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeReader
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeSource
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adds user-driven lifecycle operations to the persisted Knowledge read model.
 *
 * User deletion of an automatic page is represented by an editor-managed tombstone. The underlying
 * automatic generator therefore treats the topic as protected during rebuilds, while this repository
 * hides the tombstone from readers. Pages whose identity was created by the user can be deleted physically.
 */
class ManagingKnowledgeRepository(
  private val delegate: KnowledgeReader,
  private val database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier.shared,
) : KnowledgeRepository, KnowledgeReader by delegate {

  override suspend fun listPages(query: String): List<KnowledgePageSummary> = withContext(Dispatchers.IO) {
    val suppressedIds = loadSuppressedIds()
    delegate.listPages(query).filterNot { it.id in suppressedIds }
  }

  override suspend fun findPage(id: String): KnowledgePage? = withContext(Dispatchers.IO) {
    if (isSuppressed(id)) null else delegate.findPage(id)
  }

  override suspend fun deletePage(id: String) = withContext(Dispatchers.IO) {
    val header = loadStorageHeader(id) ?: error("削除するナレッジページが見つかりません")
    database.transaction {
      deleteOrSuppressPage(header)
    }
    dataChanges.notifyChanged()
  }

  override suspend fun splitPage(id: String, heading: String): KnowledgePage = withContext(Dispatchers.IO) {
    val page = findPage(id) ?: error("分割するナレッジページが見つかりません")
    val header = loadStorageHeader(id) ?: error("分割するナレッジページが見つかりません")
    val split = splitKnowledgePage(page, heading)
    val now = Instant.now().toString()
    val newId = "kb-split-${UUID.randomUUID().toString().replace("-", "").take(24)}"

    database.transaction {
      writeManagedPage(
        id = page.id,
        title = page.title,
        body = split.remainingBody,
        topicKind = header.topicKind,
        topicKey = header.topicKey,
        sources = page.sources,
        generatedAt = now,
      )
      writeManagedPage(
        id = newId,
        title = split.newTitle,
        body = split.newBody,
        topicKind = EDITOR_TOPIC_KIND,
        topicKey = "split:${page.id}:${split.newTitle}",
        sources = page.sources,
        generatedAt = now,
      )
    }
    dataChanges.notifyChanged()
    findPage(page.id) ?: error("分割後のナレッジページを読み込めませんでした")
  }

  override suspend fun mergePages(
    primaryId: String,
    secondaryId: String,
  ): KnowledgePage = withContext(Dispatchers.IO) {
    require(primaryId != secondaryId) { "同じ記事同士は統合できません" }
    val primary = findPage(primaryId) ?: error("統合先のナレッジページが見つかりません")
    val secondary = findPage(secondaryId) ?: error("統合するナレッジページが見つかりません")
    val primaryHeader = loadStorageHeader(primaryId) ?: error("統合先のナレッジページが見つかりません")
    val secondaryHeader = loadStorageHeader(secondaryId) ?: error("統合するナレッジページが見つかりません")
    val merged = mergeKnowledgePages(primary, secondary)
    val now = Instant.now().toString()

    database.transaction {
      writeManagedPage(
        id = primary.id,
        title = primary.title,
        body = merged.bodyMarkdown,
        topicKind = primaryHeader.topicKind,
        topicKey = primaryHeader.topicKey,
        sources = merged.sources,
        generatedAt = now,
      )
      deleteOrSuppressPage(secondaryHeader)
    }
    dataChanges.notifyChanged()
    findPage(primary.id) ?: error("統合後のナレッジページを読み込めませんでした")
  }

  private fun loadSuppressedIds(): Set<String> = database.readable.rawQuery(
    "SELECT id FROM knowledge_pages WHERE topic_kind = ?",
    arrayOf(SUPPRESSED_TOPIC_KIND),
  ).use { cursor ->
    buildSet {
      while (cursor.moveToNext()) add(cursor.getString(0))
    }
  }

  private fun isSuppressed(id: String): Boolean = database.readable.rawQuery(
    "SELECT 1 FROM knowledge_pages WHERE id = ? AND topic_kind = ? LIMIT 1",
    arrayOf(id, SUPPRESSED_TOPIC_KIND),
  ).use { cursor -> cursor.moveToFirst() }

  private fun loadStorageHeader(id: String): StorageHeader? = database.readable.rawQuery(
    "SELECT id,title,topic_kind,topic_key,editor_managed FROM knowledge_pages WHERE id = ?",
    arrayOf(id),
  ).use { cursor ->
    if (!cursor.moveToFirst()) return@use null
    StorageHeader(
      id = cursor.getString(0),
      title = cursor.getString(1),
      topicKind = cursor.getString(2),
      topicKey = cursor.getString(3),
      editorManaged = cursor.getInt(4) != 0,
    )
  }

  private fun SQLiteDatabase.deleteOrSuppressPage(header: StorageHeader) {
    delete("knowledge_page_sources", "page_id = ?", arrayOf(header.id))
    if (header.editorManaged && header.topicKind == EDITOR_TOPIC_KIND) {
      delete("knowledge_pages", "id = ?", arrayOf(header.id))
      return
    }

    update(
      "knowledge_pages",
      ContentValues().apply {
        put("body_markdown", "")
        put("topic_kind", SUPPRESSED_TOPIC_KIND)
        put("source_count", 0)
        put("source_fingerprint", managementFingerprint("deleted:${header.id}"))
        put("generated_at", Instant.now().toString())
        put("editor_managed", 1)
      },
      "id = ?",
      arrayOf(header.id),
    )
  }

  private fun SQLiteDatabase.writeManagedPage(
    id: String,
    title: String,
    body: String,
    topicKind: String,
    topicKey: String,
    sources: List<KnowledgeSource>,
    generatedAt: String,
  ) {
    val normalizedSources = sources.distinctBy(KnowledgeSource::articleId)
    val values = ContentValues().apply {
      put("id", id)
      put("title", title)
      put("body_markdown", body)
      put("topic_kind", topicKind)
      put("topic_key", topicKey)
      put("source_count", normalizedSources.size)
      put(
        "source_fingerprint",
        managementFingerprint(
          buildString {
            appendLine(title)
            appendLine(body)
            normalizedSources.forEach { appendLine(it.articleId) }
          },
        ),
      )
      put("generated_at", generatedAt)
      put("editor_managed", 1)
    }
    val updated = update("knowledge_pages", values, "id = ?", arrayOf(id))
    if (updated == 0) insertOrThrow("knowledge_pages", null, values)

    delete("knowledge_page_sources", "page_id = ?", arrayOf(id))
    normalizedSources.forEachIndexed { index, source ->
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

  private data class StorageHeader(
    val id: String,
    val title: String,
    val topicKind: String,
    val topicKey: String,
    val editorManaged: Boolean,
  )

  companion object {
    private const val EDITOR_TOPIC_KIND = "llm"
    private const val SUPPRESSED_TOPIC_KIND = "__user_deleted__"
  }
}

internal data class KnowledgeSplitContent(
  val remainingBody: String,
  val newTitle: String,
  val newBody: String,
)

internal data class KnowledgeMergeContent(
  val bodyMarkdown: String,
  val sources: List<KnowledgeSource>,
)

internal fun knowledgeSplitHeadings(bodyMarkdown: String): List<String> {
  val lines = bodyMarkdown.lines()
  return lines.mapIndexedNotNull { index, line ->
    val match = SECOND_LEVEL_HEADING.matchEntire(line.trim()) ?: return@mapIndexedNotNull null
    val before = lines.take(index).joinToString("\n").trim()
    val after = lines.drop(index + 1).joinToString("\n").trim()
    match.groupValues[1].trim().takeIf { it.isNotBlank() && before.isNotBlank() && after.isNotBlank() }
  }.distinct()
}

internal fun splitKnowledgePage(page: KnowledgePage, heading: String): KnowledgeSplitContent {
  val normalizedHeading = heading.trim()
  require(normalizedHeading.isNotBlank()) { "分割位置を選択してください" }
  val lines = page.bodyMarkdown.lines()
  val splitIndex = lines.indexOfFirst { line ->
    SECOND_LEVEL_HEADING.matchEntire(line.trim())?.groupValues?.get(1)?.trim() == normalizedHeading
  }
  require(splitIndex >= 0) { "選択した見出しが記事内に見つかりません" }

  val remainingBody = lines.take(splitIndex).joinToString("\n").trim()
  val newBody = lines.drop(splitIndex + 1).joinToString("\n").trim()
  require(remainingBody.isNotBlank() && newBody.isNotBlank()) {
    "この見出しでは記事を2つの有効な記事に分割できません"
  }
  return KnowledgeSplitContent(
    remainingBody = remainingBody,
    newTitle = normalizedHeading,
    newBody = newBody,
  )
}

internal fun mergeKnowledgePages(
  primary: KnowledgePage,
  secondary: KnowledgePage,
): KnowledgeMergeContent {
  val mergedSources = (primary.sources + secondary.sources)
    .distinctBy(KnowledgeSource::articleId)
    .mapIndexed { index, source -> source.copy(citationNumber = index + 1) }
  val citationByArticleId = mergedSources.associate { it.articleId to it.citationNumber }

  fun remap(body: String, sources: List<KnowledgeSource>): String {
    val sourceByCitation = sources.associateBy(KnowledgeSource::citationNumber)
    return CITATION.replace(body) { match ->
      val oldNumber = match.groupValues[1].toIntOrNull() ?: return@replace match.value
      val source = sourceByCitation[oldNumber] ?: return@replace match.value
      citationByArticleId[source.articleId]?.let { "[$it]" } ?: match.value
    }
  }

  val primaryBody = remap(primary.bodyMarkdown, primary.sources).trim()
  val secondaryBody = remap(secondary.bodyMarkdown, secondary.sources).trim()
  val secondaryTitle = secondary.title.replace('\n', ' ').trim()
  val mergedBody = buildString {
    append(primaryBody)
    if (isNotEmpty()) append("\n\n")
    append("## ")
    append(secondaryTitle)
    append("\n\n")
    append(secondaryBody)
  }
  return KnowledgeMergeContent(
    bodyMarkdown = mergedBody,
    sources = mergedSources,
  )
}

private fun managementFingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
  .digest(value.toByteArray(Charsets.UTF_8))
  .joinToString("") { "%02x".format(it) }

private val SECOND_LEVEL_HEADING = Regex("^##\\s+(.+?)\\s*$")
private val CITATION = Regex("\\[(\\d+)]")
