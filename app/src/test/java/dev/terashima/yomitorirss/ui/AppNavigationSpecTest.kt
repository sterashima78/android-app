package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_FOLDERS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_IMPORT_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BOOKMARK_TAGS_ROUTE
import dev.terashima.yomitorirss.feature.bookmark.BookmarkTab
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
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
import dev.terashima.yomitorirss.feature.x.X_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationSpecTest {
  @Test
  fun `各セクションの既定routeは同じセクションに属する`() {
    AppSection.entries.forEach { section ->
      assertEquals(section, section.defaultRoute().appSection())
    }
  }

  @Test
  fun `登録されたrouteには空でない画面タイトルがある`() {
    allAppRoutes.forEach { route ->
      assertTrue(route.screenTitle().isNotBlank())
    }
  }

  @Test
  fun `RSSタブの変換は往復できる`() {
    RssTab.entries.forEach { tab ->
      assertEquals(tab, tab.appRoute().rssTab())
    }
  }

  @Test
  fun `RSS設定routeはRSSセクションに属する`() {
    assertEquals(AppSection.RSS, RSS_SETTINGS_ROUTE.appSection())
    assertEquals("RSS・設定", RSS_SETTINGS_ROUTE.screenTitle())
  }

  @Test
  fun `Redditタブの変換は往復できる`() {
    RedditTab.entries.forEach { tab ->
      assertEquals(tab, tab.appRoute().redditTab())
    }
  }

  @Test
  fun `ブックマークタブの変換は往復できる`() {
    BookmarkTab.entries.forEach { tab ->
      assertEquals(tab, tab.appRoute().bookmarkTab())
    }
  }

  @Test
  fun `Xだけがグローバル上部バーを使わない`() {
    allAppRoutes.forEach { route ->
      if (route == X_ROUTE) {
        assertFalse(route.usesGlobalTopBar())
      } else {
        assertTrue(route.usesGlobalTopBar())
      }
    }
  }

  @Test
  fun `要約オーバーレイは要約アクションを持つrouteだけで有効になる`() {
    val expected = setOf(
      INTEGRATED_ROUTE,
      RSS_UNREAD_ROUTE,
      RSS_READ_LATER_ROUTE,
      REDDIT_UNREAD_ROUTE,
      REDDIT_READ_LATER_ROUTE,
      BOOKMARKS_ROUTE,
      BOOKMARK_TAGS_ROUTE,
    )

    allAppRoutes.forEach { route ->
      assertEquals(route in expected, route.usesSummaryOverlay())
    }
  }

  @Test
  fun `ブックマーク編集オーバーレイは編集操作を持つrouteだけで有効になる`() {
    val expected = setOf(
      RSS_UNREAD_ROUTE,
      RSS_READ_LATER_ROUTE,
      BOOKMARKS_ROUTE,
      BOOKMARK_TAGS_ROUTE,
    )

    allAppRoutes.forEach { route ->
      assertEquals(route in expected, route.usesBookmarkEditOverlay())
    }
  }

  @Test
  fun `feature message sourceはactive routeのcapabilityだけを宣言する`() {
    val expected = mapOf(
      INTEGRATED_ROUTE to setOf(
        FeatureMessageSource.RSS,
        FeatureMessageSource.REDDIT,
        FeatureMessageSource.FEED,
        FeatureMessageSource.SUMMARY,
      ),
      RSS_UNREAD_ROUTE to setOf(
        FeatureMessageSource.RSS,
        FeatureMessageSource.FEED,
        FeatureMessageSource.SUMMARY,
      ),
      RSS_READ_LATER_ROUTE to setOf(
        FeatureMessageSource.RSS,
        FeatureMessageSource.FEED,
        FeatureMessageSource.SUMMARY,
      ),
      RSS_FEEDS_ROUTE to setOf(FeatureMessageSource.FEED),
      RSS_SETTINGS_ROUTE to setOf(FeatureMessageSource.FEED),
      REDDIT_UNREAD_ROUTE to setOf(FeatureMessageSource.REDDIT, FeatureMessageSource.SUMMARY),
      REDDIT_READ_LATER_ROUTE to setOf(FeatureMessageSource.REDDIT, FeatureMessageSource.SUMMARY),
      REDDIT_SUBSCRIPTIONS_ROUTE to setOf(FeatureMessageSource.REDDIT),
      BOOKMARKS_ROUTE to setOf(FeatureMessageSource.BOOKMARK, FeatureMessageSource.SUMMARY),
      BOOKMARK_TAGS_ROUTE to setOf(FeatureMessageSource.BOOKMARK, FeatureMessageSource.SUMMARY),
      BOOKMARK_FOLDERS_ROUTE to setOf(FeatureMessageSource.BOOKMARK),
      BOOKMARK_IMPORT_ROUTE to setOf(FeatureMessageSource.BOOKMARK),
      SETTINGS_ROUTE to setOf(FeatureMessageSource.BACKUP, FeatureMessageSource.AI_SETTINGS),
    )

    allAppRoutes.forEach { route ->
      assertEquals(expected[route].orEmpty(), route.featureMessageSources())
    }
  }
}
