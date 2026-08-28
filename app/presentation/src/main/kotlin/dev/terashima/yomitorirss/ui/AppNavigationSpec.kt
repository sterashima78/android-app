package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.asset.ASSET_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_FOLDERS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_IMPORT_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_TAGS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.bookmarkDestinationTitle
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
import dev.terashima.yomitorirss.feature.reddit.redditDestinationTitle
import dev.terashima.yomitorirss.feature.rss.RSS_FEEDS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_READ_LATER_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.rss.RSS_UNREAD_ROUTE
import dev.terashima.yomitorirss.feature.rss.rssDestinationTitle
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

internal fun String.screenTitle(): String = rssDestinationTitle(this)
  ?: redditDestinationTitle(this)
  ?: bookmarkDestinationTitle(this)
  ?: when (this) {
    INTEGRATED_ROUTE -> "統合ビュー"
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
