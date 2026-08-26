package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.bookmark.BookmarkTab
import dev.terashima.yomitorirss.feature.reddit.RedditTab
import dev.terashima.yomitorirss.feature.rss.RssTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationSpecTest {
  @Test
  fun `各セクションの既定タブは同じセクションに属する`() {
    AppSection.entries.forEach { section ->
      assertEquals(section, section.defaultTab().appSection())
    }
  }

  @Test
  fun `各メインタブには空でない画面タイトルがある`() {
    MainTab.entries.forEach { tab ->
      assertTrue(tab.screenTitle().isNotBlank())
    }
  }

  @Test
  fun `RSSタブの変換は往復できる`() {
    RssTab.entries.forEach { tab ->
      assertEquals(tab, tab.mainTab().rssTab())
    }
  }

  @Test
  fun `RSS設定タブはRSSセクションに属する`() {
    assertEquals(AppSection.RSS, MainTab.RSS_SETTINGS.appSection())
    assertEquals("RSS・設定", MainTab.RSS_SETTINGS.screenTitle())
  }

  @Test
  fun `Redditタブの変換は往復できる`() {
    RedditTab.entries.forEach { tab ->
      assertEquals(tab, tab.mainTab().redditTab())
    }
  }

  @Test
  fun `ブックマークタブの変換は往復できる`() {
    BookmarkTab.entries.forEach { tab ->
      assertEquals(tab, tab.mainTab().bookmarkTab())
    }
  }

  @Test
  fun `Xだけがグローバル上部バーを使わない`() {
    MainTab.entries.forEach { tab ->
      if (tab == MainTab.X) {
        assertFalse(tab.usesGlobalTopBar())
      } else {
        assertTrue(tab.usesGlobalTopBar())
      }
    }
  }

  @Test
  fun `要約オーバーレイは要約アクションを持つタブだけで有効になる`() {
    val expected = setOf(
      MainTab.INTEGRATED,
      MainTab.UNREAD,
      MainTab.READ_LATER,
      MainTab.REDDIT_UNREAD,
      MainTab.REDDIT_READ_LATER,
      MainTab.SAVED,
      MainTab.TAGS,
    )

    MainTab.entries.forEach { tab ->
      assertEquals(tab in expected, tab.usesSummaryOverlay())
    }
  }

  @Test
  fun `ブックマーク編集オーバーレイは編集操作を持つタブだけで有効になる`() {
    val expected = setOf(
      MainTab.UNREAD,
      MainTab.READ_LATER,
      MainTab.SAVED,
      MainTab.TAGS,
    )

    MainTab.entries.forEach { tab ->
      assertEquals(tab in expected, tab.usesBookmarkEditOverlay())
    }
  }

  @Test
  fun `feature message sourceはactive tabのcapabilityだけを宣言する`() {
    val expected = mapOf(
      MainTab.INTEGRATED to setOf(
        FeatureMessageSource.RSS,
        FeatureMessageSource.REDDIT,
        FeatureMessageSource.FEED,
        FeatureMessageSource.SUMMARY,
      ),
      MainTab.UNREAD to setOf(
        FeatureMessageSource.RSS,
        FeatureMessageSource.FEED,
        FeatureMessageSource.SUMMARY,
      ),
      MainTab.READ_LATER to setOf(
        FeatureMessageSource.RSS,
        FeatureMessageSource.FEED,
        FeatureMessageSource.SUMMARY,
      ),
      MainTab.FEEDS to setOf(FeatureMessageSource.FEED),
      MainTab.RSS_SETTINGS to setOf(FeatureMessageSource.FEED),
      MainTab.REDDIT_UNREAD to setOf(FeatureMessageSource.REDDIT, FeatureMessageSource.SUMMARY),
      MainTab.REDDIT_READ_LATER to setOf(FeatureMessageSource.REDDIT, FeatureMessageSource.SUMMARY),
      MainTab.REDDIT_SUBSCRIPTIONS to setOf(FeatureMessageSource.REDDIT),
      MainTab.SAVED to setOf(FeatureMessageSource.BOOKMARK, FeatureMessageSource.SUMMARY),
      MainTab.TAGS to setOf(FeatureMessageSource.BOOKMARK, FeatureMessageSource.SUMMARY),
      MainTab.FOLDERS to setOf(FeatureMessageSource.BOOKMARK),
      MainTab.BOOKMARK_IMPORT to setOf(FeatureMessageSource.BOOKMARK),
      MainTab.SETTINGS to setOf(FeatureMessageSource.BACKUP, FeatureMessageSource.AI_SETTINGS),
    )

    MainTab.entries.forEach { tab ->
      assertEquals(expected[tab].orEmpty(), tab.featureMessageSources())
    }
  }
}
