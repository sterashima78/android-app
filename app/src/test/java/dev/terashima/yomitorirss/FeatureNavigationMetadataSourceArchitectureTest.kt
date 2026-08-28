package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureNavigationMetadataSourceArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `multi route featureのtab変換とtitleはowning featureが所有する`() {
    val appSpec = File(
      repositoryRoot,
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppNavigationSpec.kt",
    ).readText()
    val appChrome = File(
      repositoryRoot,
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppNavigationChrome.kt",
    ).readText()

    listOf(
      "internal fun String.rssTab",
      "internal fun String.redditTab",
      "internal fun String.bookmarkTab",
      "internal fun RssTab.appRoute",
      "internal fun RedditTab.appRoute",
      "internal fun BookmarkTab.appRoute",
    ).forEach { legacyMapping ->
      assertFalse("app presentation must not redefine feature-local navigation metadata: $legacyMapping", legacyMapping in appSpec)
    }

    listOf(
      "rssDestinationTitle(this)",
      "redditDestinationTitle(this)",
      "bookmarkDestinationTitle(this)",
    ).forEach { featureTitleContract ->
      assertTrue("app title composition must consume feature metadata: $featureTitleContract", featureTitleContract in appSpec)
    }

    listOf(
      "rssTabForRoute(selectedRoute)",
      "routeForRssTab(tab)",
      "redditTabForRoute(selectedRoute)",
      "routeForRedditTab(tab)",
      "bookmarkTabForRoute(selectedRoute)",
      "routeForBookmarkTab(tab)",
    ).forEach { featureTabContract ->
      assertTrue("app chrome must consume feature tab contract: $featureTabContract", featureTabContract in appChrome)
    }

    val featureContracts = mapOf(
      "feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/NavigationDestination.kt" to listOf(
        "fun rssTabForRoute(",
        "fun routeForRssTab(",
        "fun rssDestinationTitle(",
      ),
      "feature/reddit/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/NavigationDestination.kt" to listOf(
        "fun redditTabForRoute(",
        "fun routeForRedditTab(",
        "fun redditDestinationTitle(",
      ),
      "feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/NavigationDestination.kt" to listOf(
        "fun bookmarkTabForRoute(",
        "fun routeForBookmarkTab(",
        "fun bookmarkDestinationTitle(",
      ),
    )

    featureContracts.forEach { (path, contracts) ->
      val source = File(repositoryRoot, path).readText()
      contracts.forEach { contract ->
        assertTrue("owning feature destination contract must expose $contract in $path", contract in source)
      }
    }
  }
}
