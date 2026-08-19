package dev.terashima.yomitorirss.feature.knowledge

interface KnowledgeRepositoryProvider {
  val knowledgeRepository: KnowledgeRepository
  val buildKnowledgeUseCase: BuildKnowledgeUseCase
}
