package dev.terashima.yomitorirss.feature.rss

const val RSS_UNREAD_ROUTE = "rss/unread"
const val RSS_READ_LATER_ROUTE = "rss/read-later"
const val RSS_FEEDS_ROUTE = "rss/feeds"
const val RSS_SETTINGS_ROUTE = "rss/settings"

fun rssTabForRoute(route: String): RssTab? = when (route) {
  RSS_UNREAD_ROUTE -> RssTab.UNREAD
  RSS_READ_LATER_ROUTE -> RssTab.READ_LATER
  RSS_FEEDS_ROUTE -> RssTab.FEEDS
  RSS_SETTINGS_ROUTE -> RssTab.SETTINGS
  else -> null
}

fun routeForRssTab(tab: RssTab): String = when (tab) {
  RssTab.UNREAD -> RSS_UNREAD_ROUTE
  RssTab.READ_LATER -> RSS_READ_LATER_ROUTE
  RssTab.FEEDS -> RSS_FEEDS_ROUTE
  RssTab.SETTINGS -> RSS_SETTINGS_ROUTE
}

fun rssDestinationTitle(route: String): String? = when (route) {
  RSS_UNREAD_ROUTE -> "RSS・未読"
  RSS_READ_LATER_ROUTE -> "RSS・あとで読む"
  RSS_FEEDS_ROUTE -> "RSS・フィード管理"
  RSS_SETTINGS_ROUTE -> "RSS・設定"
  else -> null
}
