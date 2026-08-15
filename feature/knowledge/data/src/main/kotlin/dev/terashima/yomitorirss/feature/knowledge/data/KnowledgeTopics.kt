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
    sources
      .sortedBy(KnowledgeGenerationSource::articleId)
      .joinToString("\n") { "${it.articleId}\u0000${it.summary}" },
  )
}

internal fun buildKnowledgeTopics(sources: List<KnowledgeGenerationSource>): List<KnowledgeTopic> {
  val grouped = linkedMapOf<TopicIdentity, MutableList<KnowledgeGenerationSource>>()
  sources.forEach { source ->
    val tags = source.tags.map(String::trim).filter(String::isNotBlank).distinctBy { it.lowercase() }
    val identities = when {
      tags.isNotEmpty() -> tags.map { TopicIdentity("tag", it.lowercase(), it) }
      !source.folderName.isNullOrBlank() -> listOf(
        TopicIdentity("folder", source.folderName.trim().lowercase(), source.folderName.trim()),
      )
      else -> listOf(
        TopicIdentity("source", source.sourceTitle.trim().lowercase(), source.sourceTitle.trim()),
      )
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
        .sortedWith(compareByDescending<KnowledgeGenerationSource> { it.savedAt }.thenBy { it.title }),
    )
  }.sortedWith(compareByDescending<KnowledgeTopic> { it.sources.size }.thenBy { it.title.lowercase() })
}

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
  .digest(value.toByteArray(Charsets.UTF_8))
  .joinToString("") { "%02x".format(it) }

private data class TopicIdentity(
  val kind: String,
  val key: String,
  val title: String,
)
