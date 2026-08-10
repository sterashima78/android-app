package dev.terashima.yomitorirss.feature.bookmark

import dev.terashima.yomitorirss.feature.article.Article

const val READ_LATER_FOLDER_ID = "__read_later__"
const val UNCATEGORIZED_FOLDER_ID = "__uncategorized__"
const val READ_LATER_FOLDER_KIND = "read_later"

data class Tag(
  val id: String,
  val name: String,
  val normalizedName: String,
  val createdAt: String,
)

data class BookmarkFolder(
  val id: String,
  val name: String,
  val normalizedName: String,
  val systemKind: String?,
  val createdAt: String,
) {
  val isSystem: Boolean get() = systemKind != null
}

data class BookmarkedArticle(
  val article: Article,
  val savedAt: String,
  val tags: List<Tag> = emptyList(),
  val folder: BookmarkFolder? = null,
)

data class BookmarkImportResult(
  val added: Int,
  val duplicates: Int,
  val skipped: Int,
)

enum class BookmarkSaveResult {
  ADDED,
  ALREADY_BOOKMARKED,
}
