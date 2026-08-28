package dev.terashima.yomitorirss.feature.bookmark

const val BOOKMARKS_ROUTE = "bookmarks"
const val BOOKMARK_FOLDERS_ROUTE = "bookmarks/folders"
const val BOOKMARK_TAGS_ROUTE = "bookmarks/tags"
const val BOOKMARK_IMPORT_ROUTE = "bookmarks/import"

fun bookmarkTabForRoute(route: String): BookmarkTab? = when (route) {
  BOOKMARKS_ROUTE -> BookmarkTab.BOOKMARKS
  BOOKMARK_FOLDERS_ROUTE -> BookmarkTab.FOLDERS
  BOOKMARK_TAGS_ROUTE -> BookmarkTab.TAGS
  BOOKMARK_IMPORT_ROUTE -> BookmarkTab.IMPORT
  else -> null
}

fun routeForBookmarkTab(tab: BookmarkTab): String = when (tab) {
  BookmarkTab.BOOKMARKS -> BOOKMARKS_ROUTE
  BookmarkTab.FOLDERS -> BOOKMARK_FOLDERS_ROUTE
  BookmarkTab.TAGS -> BOOKMARK_TAGS_ROUTE
  BookmarkTab.IMPORT -> BOOKMARK_IMPORT_ROUTE
}

fun bookmarkDestinationTitle(route: String): String? = when (route) {
  BOOKMARKS_ROUTE -> "ブックマーク・一覧"
  BOOKMARK_FOLDERS_ROUTE -> "ブックマーク・フォルダ"
  BOOKMARK_TAGS_ROUTE -> "ブックマーク・タグ"
  BOOKMARK_IMPORT_ROUTE -> "ブックマーク・インポート"
  else -> null
}
