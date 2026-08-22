package dev.terashima.yomitorirss.feature.summary

import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkContentQuery
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEnrichmentRepository

data class SummaryRuntimeDependencies(
  val articleRepository: ArticleRepository,
  val bookmarkContentQuery: BookmarkContentQuery,
  val bookmarkEnrichmentRepository: BookmarkEnrichmentRepository,
)
