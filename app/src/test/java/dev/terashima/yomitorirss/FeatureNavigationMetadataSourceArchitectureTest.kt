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

  @Test
  fun `single route featureのtitleはowning featureが所有する`() {
    val appSpec = File(
      repositoryRoot,
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppNavigationSpec.kt",
    ).readText()
    val featureContracts = mapOf(
      "feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/NavigationDestination.kt" to
        ("INTEGRATED_ROUTE -> INTEGRATED_TITLE" to "const val INTEGRATED_TITLE = \"統合ビュー\""),
      "feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/NavigationDestination.kt" to
        ("LIBRARY_ROUTE -> LIBRARY_TITLE" to "const val LIBRARY_TITLE = \"蔵書\""),
      "feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/NavigationDestination.kt" to
        ("KNOWLEDGE_ROUTE -> KNOWLEDGE_TITLE" to "const val KNOWLEDGE_TITLE = \"ナレッジ\""),
      "feature/asset/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/asset/NavigationDestination.kt" to
        ("ASSET_ROUTE -> ASSET_TITLE" to "const val ASSET_TITLE = \"資産\""),
      "feature/mail/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/NavigationDestination.kt" to
        ("MAIL_ROUTE -> MAIL_TITLE" to "const val MAIL_TITLE = \"メール\""),
      "feature/youtube/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/youtube/NavigationDestination.kt" to
        ("YOUTUBE_ROUTE -> YOUTUBE_TITLE" to "const val YOUTUBE_TITLE = \"YouTube\""),
      "feature/x/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/x/NavigationDestination.kt" to
        ("X_ROUTE -> X_TITLE" to "const val X_TITLE = \"X\""),
      "feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/NavigationDestination.kt" to
        ("TASKS_ROUTE -> TASKS_TITLE" to "const val TASKS_TITLE = \"タスク\""),
      "feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/NavigationDestination.kt" to
        ("CALENDAR_ROUTE -> CALENDAR_TITLE" to "const val CALENDAR_TITLE = \"カレンダー\""),
      "feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/NavigationDestination.kt" to
        ("GAME_ROUTE -> GAME_TITLE" to "const val GAME_TITLE = \"ゲーム\""),
      "feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/NavigationDestination.kt" to
        ("HEALTH_ROUTE -> HEALTH_TITLE" to "const val HEALTH_TITLE = \"ヘルス\""),
      "feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/NavigationDestination.kt" to
        ("WORKOUT_ROUTE -> WORKOUT_TITLE" to "const val WORKOUT_TITLE = \"ワークアウト\""),
      "feature/chat/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/chat/NavigationDestination.kt" to
        ("CHAT_ROUTE -> CHAT_TITLE" to "const val CHAT_TITLE = \"AIチャット\""),
      "feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/NavigationDestination.kt" to
        ("SETTINGS_ROUTE -> SETTINGS_TITLE" to "const val SETTINGS_TITLE = \"設定\""),
    )

    featureContracts.forEach { (path, contracts) ->
      val (appMapping, featureTitle) = contracts
      val source = File(repositoryRoot, path).readText()
      assertTrue("app title composition must consume owning feature title: $appMapping", appMapping in appSpec)
      assertTrue("owning feature destination contract must expose title in $path", featureTitle in source)
    }
  }
}
