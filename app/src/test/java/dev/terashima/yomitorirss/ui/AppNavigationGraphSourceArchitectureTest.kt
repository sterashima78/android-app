package dev.terashima.yomitorirss.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationGraphSourceArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  private val appUiRoot = File(
    repositoryRoot,
    "app/src/main/java/dev/terashima/yomitorirss/ui",
  )

  @Test
  fun `AppNavHostはroot graph compositionだけを所有する`() {
    val source = File(appUiRoot, "AppNavHost.kt").readText()

    assertTrue("AppNavHost must own NavHost", "NavHost(" in source)
    listOf(
      "registerHomeDestination(",
      "registerRssDestinations(",
      "registerRedditDestinations(",
      "registerBookmarkDestinations(",
      "registerSingleFeatureDestinations(",
    ).forEach { registration ->
      assertTrue("AppNavHost must compose $registration", registration in source)
    }
    assertFalse(
      "destination registration must stay split from AppNavHost",
      "composable(" in source,
    )
  }

  @Test
  fun `app owned navigation graphは責務別registrationへ分割する`() {
    val graphFiles = listOf(
      "AppHomeNavGraph.kt",
      "AppRssNavGraph.kt",
      "AppRedditNavGraph.kt",
      "AppBookmarkNavGraph.kt",
      "AppSingleFeatureNavGraph.kt",
    )

    graphFiles.forEach { fileName ->
      val file = File(appUiRoot, fileName)
      assertTrue("navigation graph registration must exist: $fileName", file.isFile)
      assertTrue(
        "navigation graph registration must register destinations: $fileName",
        "composable(" in file.readText(),
      )
    }
  }

  @Test
  fun `navigation graph registrationはdestination dispatch前にViewModelを生成しない`() {
    listOf(
      "AppHomeNavGraph.kt",
      "AppRssNavGraph.kt",
      "AppRedditNavGraph.kt",
      "AppBookmarkNavGraph.kt",
      "AppSingleFeatureNavGraph.kt",
    ).forEach { fileName ->
      val source = File(appUiRoot, fileName).readText()
      val beforeFirstDestination = source.substringBefore("composable(")
      assertFalse(
        "$fileName must resolve feature ViewModels inside a destination",
        Regex("=\\s*viewModel\\(").containsMatchIn(beforeFirstDestination),
      )
    }
  }
}
