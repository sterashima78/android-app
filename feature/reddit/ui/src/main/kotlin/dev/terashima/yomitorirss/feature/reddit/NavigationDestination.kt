package dev.terashima.yomitorirss.feature.reddit

const val REDDIT_UNREAD_ROUTE = "reddit/unread"
const val REDDIT_READ_LATER_ROUTE = "reddit/read-later"
const val REDDIT_SUBSCRIPTIONS_ROUTE = "reddit/subscriptions"

fun redditTabForRoute(route: String): RedditTab? = when (route) {
  REDDIT_UNREAD_ROUTE -> RedditTab.UNREAD
  REDDIT_READ_LATER_ROUTE -> RedditTab.READ_LATER
  REDDIT_SUBSCRIPTIONS_ROUTE -> RedditTab.SUBSCRIPTIONS
  else -> null
}

fun routeForRedditTab(tab: RedditTab): String = when (tab) {
  RedditTab.UNREAD -> REDDIT_UNREAD_ROUTE
  RedditTab.READ_LATER -> REDDIT_READ_LATER_ROUTE
  RedditTab.SUBSCRIPTIONS -> REDDIT_SUBSCRIPTIONS_ROUTE
}

fun redditDestinationTitle(route: String): String? = when (route) {
  REDDIT_UNREAD_ROUTE -> "Reddit・未読"
  REDDIT_READ_LATER_ROUTE -> "Reddit・あとで読む"
  REDDIT_SUBSCRIPTIONS_ROUTE -> "Reddit・購読管理"
  else -> null
}
