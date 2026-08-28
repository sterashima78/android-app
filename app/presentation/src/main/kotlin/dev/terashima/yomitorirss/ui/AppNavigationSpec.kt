package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.asset.ASSET_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_FOLDERS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_IMPORT_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_TAGS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BookmarkTab
import dev.terashima.yomitorirss.feature.calendar.CALENDAR_ROUTE
import dev.terashima.yomitorirss.feature.chat.CHAT_ROUTE
import dev.terashima.yomitorirss.feature.game.GAME_ROUTE
import dev.terashima.yomitorirss.feature.health.HEALTH_ROUTE
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
import dev.terashima.yomitorirss.feature.knowledge.KNOWLEDGE_ROUTE
import dev.terashima.yomitorirss.feature.library.LIBRARY_ROUTE
import dev.terashima.yomitorirss.feature.mail.MAIL_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_SUBSCRIPTIONS_ROUTE
import dev.terashima.yomitorirss.feature.reddit.REDDIT_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.reddit.RedditTab
import dev.terashima.yomitorirss.feature.rss.RSS_FEEDS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.rss.RssTab
import dev.terashima.yomitorirss.feature.settings.SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.task.TASKS_ROUTE
import dev.terashima.yomitorirss.feature.workout.WORKOUT_ROUTE
import dev.terashima.yomitorirss.feature.x.X_ROUTE
import dev.terashima.yomitorirss.feature.youtube.YOUTUBE_ROUTE

internal enum class FeatureMessageSource {
  RSS,
  REDDIT,
  FEED,
  SUMMARY,
  BOOKMARK,
  BACKUP,
  AI_SETTINGS,
}

internal val allAppRoutes: Set<String> = linkedSetOf(
  INTEGRATED_ROUTE,
  RSS_UNREAD_ROUTE,
  RSS_READ_LATER_ROUTE,
  REDDIT_UNREAD_ROUTE,
  REDDIT_READ_LATER_ROUTE,
  REDDIT_SUBSCRIPTIONS_ROUTE,
  BOOKMARKS_ROUTE,
  RSS_FEEDS_ROUTE,
  RSS_SETTINGS_ROUTE,
  BOOKMARK_FOLDERS_ROUTE,
  BOOKMARK_TAGS_ROUTE,
  BOOKMARK_IMPORT_ROUTE,
  LIBRARY_ROUTE,
  KNOWLEDGE_ROUTE,
  ASSET_ROUTE,
  MAIL_ROUTE,
  YOUTUBE_ROUTE,
  X_ROUTE,
  TASKS_ROUTE,
  CALENDAR_ROUTE,
  GAME_ROUTE,
  HEALTH_ROUTE,
  WORKOUT_ROUTE,
  CHAT_ROUTE,
  SETTINGS_ROUTE,
)

internal fun String.featureMessageSources(): Set<FeatureMessageSource> = when (this) {
  INTEGRATED_ROUTE -> setOf(
    FeatureMessageSource.RSS,
    FeatureMessageSource.REDDIT,
    FeatureMessageSource.FEED,
    FeatureMessageSource.SUMMARY,
  )
  RSS_UNREAD_ROUTE,
  RSS_READ_LATER_ROUTE -> setOf(
    FeatureMessageSource.RSS,
    FeatureMessageSource.FEED,
    FeatureMessageSource.SUMMARY,
  )
  RSS_FEEDS_ROUTE,
  RSS_SETTINGS_ROUTE -> setOf(FeatureMessageSource.FEED)
  REDDIT_UNREAD_ROUTE,
  REDDIT_READ_LATER_ROUTE -> setOf(
    FeatureMessageSource.REDDIT,
    FeatureMessageSource.SUMMARY,
  )
  REDDIT_SUBSCRIPTIONS_ROUTE -> setOf(FeatureMessageSource.REDDIT)
  BOOKMARKS_ROUTE,
  BOOKMARK_TAGS_ROUTE -> setOf(
    FeatureMessageSource.BOOKMARK,
    FeatureMessageSource.SUMMARY,
  )
  BOOKMARK_FOLDERS_ROUTE,
  BOOKMARK_IMPORT_ROUTE -> setOf(FeatureMessageSource.BOOKMARK)
  SETTINGS_ROUTE -> setOf(
    FeatureMessageSource.BACKUP,
    FeatureMessageSource.AI_SETTINGS,
  )
  else -> emptySet()
}

internal fun String.usesGlobalTopBar(): Boolean = this != X_ROUTE

internal fun String.usesSummaryOverlay(): Boolean = when (this) {
  INTEGRATED_ROUTE,
  RSS_UNREAD_ROUTE,
  RSS_READ_LATER_ROUTE,
  REDDIT_UNREAD_ROUTE,
  REDDIT_READ_LATER_ROUTE,
  BOOKMARKS_ROUTE,
  BOOKMARK_TAGS_ROUTE -> true
  else -> false
}

internal fun String.usesBookmarkEditOverlay(): Boolean = when (this) {
  RSS_UNREAD_ROUTE,
  RSS_READ_LATER_ROUTE,
  BOOKMARKS_ROUTE,
  BOOKMARK_TAGS_ROUTE -> true
  else -> false
}

internal fun String.appSection(): AppSection = when (this) {
  INTEGRATED_ROUTE -> AppSection.HOME
  RSS_UNREAD_ROUTE, RSS_READ_LATER_ROUTE, RSS_FEEDS_ROUTE, RSS_SETTINGS_ROUTE -> AppSection.RSS
  REDDIT_UNREAD_ROUTE, REDDIT_READ_LATER_ROUTE, REDDIT_SUBSCRIPTIONS_ROUTE -> AppSection.REDDIT
  BOOKMARKS_ROUTE, BOOKMARK_FOLDERS_ROUTE, BOOKMARK_TAGS_ROUTE, BOOKMARK_IMPORT_ROUTE -> AppSection.BOOKMARKS
  LIBRARY_ROUTE -> AppSection.LIBRARY
  KNOWLEDGE_ROUTE -> AppSection.KNOWLEDGE
  ASSET_ROUTE -> AppSection.ASSETS
  MAIL_ROUTE -> AppSection.MAIL
  YOUTUBE_ROUTE -> AppSection.YOUTUBE
  X_ROUTE -> AppSection.X
  TASKS_ROUTE -> AppSection.TASKS
  CALENDAR_ROUTE -> AppSection.CALENDAR
  GAME_ROUTE -> AppSection.GAME
  HEALTH_ROUTE -> AppSection.HEALTH
  WORKOUT_ROUTE -> AppSection.WORKOUT
  CHAT_ROUTE -> AppSection.AI_CHAT
  SETTINGS_ROUTE -> AppSection.SETTINGS
  else -> error("Unknown app route: $this")
}

internal fun String.rssTab(): RssTab? = when (this) {
  RSS_UNREAD_ROUTE -> RssTab.UNREAD
  RSS_READ_LATER_ROUTE -> RssTab.READ_LATER
  RSS_FEEDS_ROUTE -> RssTab.FEEDS
  RSS_SETTINGS_ROUTE -> RssTab.SETTINGS
  else -> null
}

internal fun String.redditTab(): RedditTab? = when (this) {
  REDDIT_UNREAD_ROUTE -> RedditTab.UNREAD
  REDDIT_READ_LATER_ROUTE -> RedditTab.READ_LATER
  REDDIT_SUBSCRIPTIONS_ROUTE -> RedditTab.SUBSCRIPTIONS
  else -> null
}

internal fun String.bookmarkTab(): BookmarkTab? = when (this) {
  BOOKMARKS_ROUTE -> BookmarkTab.BOOKMARKS
  BOOKMARK_FOLDERS_ROUTE -> BookmarkTab.FOLDERS
  BOOKMARK_TAGS_ROUTE -> BookmarkTab.TAGS
  BOOKMARK_IMPORT_ROUTE -> BookmarkTab.IMPORT
  else -> null
}

internal fun String.screenTitle(): String = when (this) {
  INTEGRATED_ROUTE -> "統合ビュー"
  RSS_UNREAD_ROUTE -> "RSS・未読"
  RSS_READ_LATER_ROUTE -> "RSS・あとで読む"
  REDDIT_UNREAD_ROUTE -> "Reddit・未読"
  REDDIT_READ_LATER_ROUTE -> "Reddit・あとで読む"
  REDDIT_SUBSCRIPTIONS_ROUTE -> "Reddit・購読管理"
  BOOKMARKS_ROUTE -> "ブックマーク・一覧"
  BOOKMARK_FOLDERS_ROUTE -> "ブックマーク・フォルダ"
  BOOKMARK_TAGS_ROUTE -> "ブックマーク・タグ"
  BOOKMARK_IMPORT_ROUTE -> "ブックマーク・インポート"
  LIBRARY_ROUTE -> "蔵書"
  KNOWLEDGE_ROUTE -> "ナレッジ"
  ASSET_ROUTE -> "資産"
  MAIL_ROUTE -> "メール"
  YOUTUBE_ROUTE -> "YouTube"
  X_ROUTE -> "X"
  TASKS_ROUTE -> "タスク"
  CALENDAR_ROUTE -> "カレンダー"
  GAME_ROUTE -> "ゲーム"
  HEALTH_ROUTE -> "ヘルス"
  WORKOUT_ROUTE -> "ワークアウト"
  CHAT_ROUTE -> "AIチャット"
  RSS_FEEDS_ROUTE -> "RSS・フィード管理"
  RSS_SETTINGS_ROUTE -> "RSS・設定"
  SETTINGS_ROUTE -> "設定"
  else -> error("Unknown app route: $this")
}

internal fun AppSection.defaultRoute(): String = when (this) {
  AppSection.HOME -> INTEGRATED_ROUTE
  AppSection.RSS -> RSS_UNREAD_ROUTE
  AppSection.REDDIT -> REDDIT_UNREAD_ROUTE
  AppSection.BOOKMARKS -> BOOKMARKS_ROUTE
  AppSection.LIBRARY -> LIBRARY_ROUTE
  AppSection.KNOWLEDGE -> KNOWLEDGE_ROUTE
  AppSection.ASSETS -> ASSET_ROUTE
  AppSection.MAIL -> MAIL_ROUTE
  AppSection.YOUTUBE -> YOUTUBE_ROUTE
  AppSection.X -> X_ROUTE
  AppSection.TASKS -> TASKS_ROUTE
  AppSection.CALENDAR -> CALENDAR_ROUTE
  AppSection.GAME -> GAME_ROUTE
  AppSection.HEALTH -> HEALTH_ROUTE
  AppSection.WORKOUT -> WORKOUT_ROUTE
  AppSection.AI_CHAT -> CHAT_ROUTE
  AppSection.SETTINGS -> SETTINGS_ROUTE
}

internal fun RssTab.appRoute(): String = when (this) {
  RssTab.UNREAD -> RSS_UNREAD_ROUTE
  RssTab.READ_LATER -> RSS_READ_LATER_ROUTE
  RssTab.FEEDS -> RSS_FEEDS_ROUTE
  RssTab.SETTINGS -> RSS_SETTINGS_ROUTE
}

internal fun RedditTab.appRoute(): String = when (this) {
  RedditTab.UNREAD -> REDDIT_UNREAD_ROUTE
  RedditTab.READ_LATER -> REDDIT_READ_LATER_ROUTE
  RedditTab.SUBSCRIPTIONS -> REDDIT_SUBSCRIPTIONS_ROUTE
}

internal fun BookmarkTab.appRoute(): String = when (this) {
  BookmarkTab.BOOKMARKS -> BOOKMARKS_ROUTE
  BookmarkTab.FOLDERS -> BOOKMARK_FOLDERS_ROUTE
  BookmarkTab.TAGS -> BOOKMARK_TAGS_ROUTE
  BookmarkTab.IMPORT -> BOOKMARK_IMPORT_ROUTE
}
