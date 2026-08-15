package dev.terashima.yomitorirss.feature.knowledge.data

import java.security.MessageDigest

internal data class KnowledgeGenerationSource(
  val articleId: String,
  val title: String,
  val url: String,
  val sourceTitle: String,
  val savedAt: String,
  val summary: String,
  val tags: List<String>,
  val folderName: String?,
)

internal data class KnowledgeTopic(
  val id: String,
  val kind: String,
  val key: String,
  val title: String,
  val sources: List<KnowledgeGenerationSource>,
) {
  val sourceFingerprint: String = sha256(
    buildString {
      append(kind)
      append('\u0000')
      append(key)
      append('\u0000')
      appendLine(title)
      sources
        .sortedBy(KnowledgeGenerationSource::articleId)
        .forEach { source ->
          appendLine(
            listOf(
              source.articleId,
              source.title,
              source.url,
              source.sourceTitle,
              source.savedAt,
              source.summary,
            ).joinToString("\u0000"),
          )
        }
    },
  )
}

internal data class GeneratedKnowledgeDocument(
  val title: String,
  val bodyMarkdown: String,
)

internal fun buildKnowledgeTopics(sources: List<KnowledgeGenerationSource>): List<KnowledgeTopic> {
  val grouped = linkedMapOf<TopicIdentity, MutableList<KnowledgeGenerationSource>>()
  sources.forEach { source ->
    val tags = source.tags.map(String::trim).filter(String::isNotBlank).distinctBy { it.lowercase() }
    val folderName = source.folderName?.trim().orEmpty()
    val sourceTitle = source.sourceTitle.trim().ifBlank { "その他" }
    val identities = when {
      tags.isNotEmpty() -> tags.map { TopicIdentity("tag", it.lowercase(), it) }
      folderName.isNotEmpty() -> listOf(TopicIdentity("folder", folderName.lowercase(), folderName))
      else -> listOf(TopicIdentity("source", sourceTitle.lowercase(), sourceTitle))
    }
    identities.forEach { identity -> grouped.getOrPut(identity) { mutableListOf() } += source }
  }

  return grouped.map { (identity, topicSources) ->
    KnowledgeTopic(
      id = "kb-${sha256("${identity.kind}:${identity.key}").take(24)}",
      kind = identity.kind,
      key = identity.key,
      title = identity.title,
      sources = topicSources.distinctBy(KnowledgeGenerationSource::articleId)
        .sortedWith(compareByDescending<KnowledgeGenerationSource> { it.savedAt }.thenBy { it.title })
        .take(MAX_SOURCES_PER_TOPIC),
    )
  }.sortedWith(compareByDescending<KnowledgeTopic> { it.sources.size }.thenBy { it.title.lowercase() })
}

internal fun selectKnowledgeSources(
  query: String,
  sources: List<KnowledgeGenerationSource>,
  preferredArticleIds: Set<String> = emptySet(),
  limit: Int = MAX_SOURCES_PER_TOPIC,
): List<KnowledgeGenerationSource> {
  require(limit > 0)
  val terms = knowledgeSearchTerms(query)
  val scored = sources.distinctBy(KnowledgeGenerationSource::articleId).map { source ->
    val title = source.title.lowercase()
    val sourceTitle = source.sourceTitle.lowercase()
    val folder = source.folderName.orEmpty().lowercase()
    val tags = source.tags.joinToString(" ").lowercase()
    val summary = source.summary.lowercase()
    var score = if (source.articleId in preferredArticleIds) 100 else 0
    terms.forEach { term ->
      if (term in title) score += 12
      if (term in tags) score += 10
      if (term in folder) score += 8
      if (term in sourceTitle) score += 6
      if (term in summary) score += 2
    }
    ScoredSource(source, score)
  }

  val matching = scored.filter { it.score > 0 }
  return (matching.ifEmpty { scored })
    .sortedWith(
      compareByDescending<ScoredSource> { it.score }
        .thenByDescending { it.source.savedAt }
        .thenBy { it.source.title.lowercase() },
    )
    .take(limit)
    .map(ScoredSource::source)
}

internal fun parseGeneratedKnowledgeDocument(
  raw: String,
  fallbackTitle: String,
): GeneratedKnowledgeDocument {
  val normalized = stripMarkdownFence(raw).trim()
  require(normalized.isNotBlank()) { "ナレッジページの生成結果が空でした" }
  val lines = normalized.lines()
  val firstContentIndex = lines.indexOfFirst { it.isNotBlank() }
  if (firstContentIndex >= 0) {
    val first = lines[firstContentIndex].trim()
    if (first.startsWith("# ")) {
      val title = first.removePrefix("# ").trim().ifBlank { fallbackTitle }
      val body = lines.drop(firstContentIndex + 1).joinToString("\n").trim()
      return GeneratedKnowledgeDocument(
        title = title.take(MAX_TITLE_LENGTH),
        bodyMarkdown = body.ifBlank { normalized },
      )
    }
  }
  return GeneratedKnowledgeDocument(
    title = fallbackTitle.trim().ifBlank { "新しいナレッジ" }.take(MAX_TITLE_LENGTH),
    bodyMarkdown = normalized,
  )
}

internal fun fallbackKnowledgeTitle(request: String): String {
  val firstLine = request.lineSequence().firstOrNull().orEmpty().trim()
  val simplified = firstLine
    .removeSuffix("についてまとめて")
    .removeSuffix("についてまとめる")
    .removeSuffix("をまとめて")
    .removeSuffix("をまとめる")
    .removeSuffix("の記事を作成")
    .trim(' ', '。', '、')
  return simplified.ifBlank { "新しいナレッジ" }.take(MAX_TITLE_LENGTH)
}

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
  .digest(value.toByteArray(Charsets.UTF_8))
  .joinToString("") { "%02x".format(it) }

private fun knowledgeSearchTerms(query: String): List<String> {
  val cleaned = query.lowercase()
    .replace("について", " ")
    .replace("まとめて", " ")
    .replace("まとめる", " ")
    .replace("記事", " ")
    .replace("作成", " ")
    .replace("詳しく", " ")
  return cleaned
    .split(Regex("[\\s\\p{Punct}、。・「」『』（）【】]+"))
    .map(String::trim)
    .filter { it.length >= 2 || it.any(Char::isDigit) }
    .distinct()
    .take(MAX_SEARCH_TERMS)
}

private fun stripMarkdownFence(value: String): String {
  val lines = value.trim().lines()
  if (lines.size < 2 || !lines.first().trim().startsWith("```")) return value
  val lastIndex = lines.indexOfLast { it.isNotBlank() }
  if (lastIndex <= 0 || lines[lastIndex].trim() != "```") return value
  return lines.subList(1, lastIndex).joinToString("\n")
}

private data class TopicIdentity(
  val kind: String,
  val key: String,
  val title: String,
)

private data class ScoredSource(
  val source: KnowledgeGenerationSource,
  val score: Int,
)

internal const val MAX_SOURCES_PER_TOPIC = 12
private const val MAX_SEARCH_TERMS = 24
private const val MAX_TITLE_LENGTH = 80
