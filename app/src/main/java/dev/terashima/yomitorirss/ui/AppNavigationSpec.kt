package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.bookmark.BookmarkTab
import dev.terashima.yomitorirss.feature.reddit.RedditTab
import dev.terashima.yomitorirss.feature.rss.RssTab

internal fun MainTab.usesGlobalTopBar(): Boolean = this != MainTab.X

internal fun MainTab.usesSummaryOverlay(): Boolean = when (this) {
  MainTab.INTEGRATED,
  MainTab.UNREAD,
  MainTab.READ_LATER,
  MainTab.REDDIT_UNREAD,
  MainTab.REDDIT_READ_LATER,
  MainTab.SAVED,
  MainTab.TAGS -> true

  else -> false
}

internal fun MainTab.usesBookmarkEditOverlay(): Boolean = when (this) {
  MainTab.UNREAD,
  MainTab.READ_LATER,
  MainTab.SAVED,
  MainTab.TAGS -> true

  else -> false
}

internal fun MainTab.appSection(): AppSection = when (this) {
  MainTab.INTEGRATED -> AppSection.HOME
  MainTab.UNREAD, MainTab.READ_LATER, MainTab.FEEDS -> AppSection.RSS
  MainTab.REDDIT_UNREAD, MainTab.REDDIT_READ_LATER, MainTab.REDDIT_SUBSCRIPTIONS -> AppSection.REDDIT
  MainTab.SAVED, MainTab.FOLDERS, MainTab.TAGS, MainTab.BOOKMARK_IMPORT -> AppSection.BOOKMARKS
  MainTab.LIBRARY -> AppSection.LIBRARY
  MainTab.KNOWLEDGE -> AppSection.KNOWLEDGE
  MainTab.ASSETS -> AppSection.ASSETS
  MainTab.MAIL -> AppSection.MAIL
  MainTab.YOUTUBE -> AppSection.YOUTUBE
  MainTab.X -> AppSection.X
  MainTab.TASKS -> AppSection.TASKS
  MainTab.CALENDAR -> AppSection.CALENDAR
  MainTab.GAME -> AppSection.GAME
  MainTab.HEALTH -> AppSection.HEALTH
  MainTab.WORKOUT -> AppSection.WORKOUT
  MainTab.AI_CHAT -> AppSection.AI_CHAT
  MainTab.SETTINGS -> AppSection.SETTINGS
}

internal fun MainTab.rssTab(): RssTab? = when (this) {
  MainTab.UNREAD -> RssTab.UNREAD
  MainTab.READ_LATER -> RssTab.READ_LATER
  MainTab.FEEDS -> RssTab.FEEDS
  else -> null
}

internal fun MainTab.redditTab(): RedditTab? = when (this) {
  MainTab.REDDIT_UNREAD -> RedditTab.UNREAD
  MainTab.REDDIT_READ_LATER -> RedditTab.READ_LATER
  MainTab.REDDIT_SUBSCRIPTIONS -> RedditTab.SUBSCRIPTIONS
  else -> null
}

internal fun MainTab.bookmarkTab(): BookmarkTab? = when (this) {
  MainTab.SAVED -> BookmarkTab.BOOKMARKS
  MainTab.FOLDERS -> BookmarkTab.FOLDERS
  MainTab.TAGS -> BookmarkTab.TAGS
  MainTab.BOOKMARK_IMPORT -> BookmarkTab.IMPORT
  else -> null
}

internal fun MainTab.screenTitle(): String = when (this) {
  MainTab.INTEGRATED -> "統合ビュー"
  MainTab.UNREAD -> "RSS・未読"
  MainTab.READ_LATER -> "RSS・あとで読む"
  MainTab.REDDIT_UNREAD -> "Reddit・未読"
  MainTab.REDDIT_READ_LATER -> "Reddit・あとで読む"
  MainTab.REDDIT_SUBSCRIPTIONS -> "Reddit・購読管理"
  MainTab.SAVED -> "ブックマーク・一覧"
  MainTab.FOLDERS -> "ブックマーク・フォルダ"
  MainTab.TAGS -> "ブックマーク・タグ"
  MainTab.BOOKMARK_IMPORT -> "ブックマーク・インポート"
  MainTab.LIBRARY -> "蔵書"
  MainTab.KNOWLEDGE -> "ナレッジ"
  MainTab.ASSETS -> "資産"
  MainTab.MAIL -> "メール"
  MainTab.YOUTUBE -> "YouTube"
  MainTab.X -> "X"
  MainTab.TASKS -> "タスク"
  MainTab.CALENDAR -> "カレンダー"
  MainTab.GAME -> "ゲーム"
  MainTab.HEALTH -> "ヘルス"
  MainTab.WORKOUT -> "ワークアウト"
  MainTab.AI_CHAT -> "AIチャット"
  MainTab.FEEDS -> "RSS・フィード管理"
  MainTab.SETTINGS -> "設定"
}

internal fun AppSection.defaultTab(): MainTab = when (this) {
  AppSection.HOME -> MainTab.INTEGRATED
  AppSection.RSS -> MainTab.UNREAD
  AppSection.REDDIT -> MainTab.REDDIT_UNREAD
  AppSection.BOOKMARKS -> MainTab.SAVED
  AppSection.LIBRARY -> MainTab.LIBRARY
  AppSection.KNOWLEDGE -> MainTab.KNOWLEDGE
  AppSection.ASSETS -> MainTab.ASSETS
  AppSection.MAIL -> MainTab.MAIL
  AppSection.YOUTUBE -> MainTab.YOUTUBE
  AppSection.X -> MainTab.X
  AppSection.TASKS -> MainTab.TASKS
  AppSection.CALENDAR -> MainTab.CALENDAR
  AppSection.GAME -> MainTab.GAME
  AppSection.HEALTH -> MainTab.HEALTH
  AppSection.WORKOUT -> MainTab.WORKOUT
  AppSection.AI_CHAT -> MainTab.AI_CHAT
  AppSection.SETTINGS -> MainTab.SETTINGS
}

internal fun RssTab.mainTab(): MainTab = when (this) {
  RssTab.UNREAD -> MainTab.UNREAD
  RssTab.READ_LATER -> MainTab.READ_LATER
  RssTab.FEEDS -> MainTab.FEEDS
}

internal fun RedditTab.mainTab(): MainTab = when (this) {
  RedditTab.UNREAD -> MainTab.REDDIT_UNREAD
  RedditTab.READ_LATER -> MainTab.REDDIT_READ_LATER
  RedditTab.SUBSCRIPTIONS -> MainTab.REDDIT_SUBSCRIPTIONS
}

internal fun BookmarkTab.mainTab(): MainTab = when (this) {
  BookmarkTab.BOOKMARKS -> MainTab.SAVED
  BookmarkTab.FOLDERS -> MainTab.FOLDERS
  BookmarkTab.TAGS -> MainTab.TAGS
  BookmarkTab.IMPORT -> MainTab.BOOKMARK_IMPORT
}
