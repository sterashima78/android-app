package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.bookmark.BookmarkTab
import dev.terashima.yomitorirss.feature.navigation.AppSection
import dev.terashima.yomitorirss.feature.navigation.MainTab
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
}
