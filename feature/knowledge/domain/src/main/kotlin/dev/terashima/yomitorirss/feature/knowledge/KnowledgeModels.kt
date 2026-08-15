package dev.terashima.yomitorirss.feature.knowledge

data class KnowledgePageSummary(
  val id: String,
  val title: String,
  val sourceCount: Int,
  val generatedAt: String,
)

data class KnowledgeSource(
  val citationNumber: Int,
  val articleId: String,
  val title: String,
  val url: String,
  val sourceTitle: String,
  val savedAt: String,
)

data class KnowledgePage(
  val id: String,
  val title: String,
  val bodyMarkdown: String,
  val sourceCount: Int,
  val generatedAt: String,
  val sources: List<KnowledgeSource>,
)

data class KnowledgeBuildResult(
  val generated: Int,
  val reused: Int,
  val pending: Int,
  val skippedWithoutSummary: Int,
)
