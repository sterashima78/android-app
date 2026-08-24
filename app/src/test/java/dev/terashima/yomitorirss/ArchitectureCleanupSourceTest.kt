package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureCleanupSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `AppRouteDependenciesはRedditの低レベル分類規則を再実装しない`() {
    val source = source("app/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt")

    assertTrue("route composition must consume the Reddit-owned boundary", "RedditSourceBoundary" in source)
    listOf(
      "isRedditArticle",
      "isRedditFeedUrl",
      "redditCommunityFeedUrl",
      "redditThreadId",
    ).forEach { lowLevelRule ->
      assertFalse(
        "route composition must not depend on Reddit low-level rule: $lowLevelRule",
        "feature.reddit.$lowLevelRule" in source,
      )
    }
  }

  @Test
  fun `Redditの低レベル分類APIはowner feature外へ公開利用しない`() {
    val forbiddenImports = listOf(
      "import dev.terashima.yomitorirss.feature.reddit.isRedditArticle",
      "import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl",
      "import dev.terashima.yomitorirss.feature.reddit.redditCommunityFeedUrl",
      "import dev.terashima.yomitorirss.feature.reddit.redditThreadId",
    )

    repositoryRoot.walkTopDown()
      .filter(File::isFile)
      .filter { it.extension == "kt" }
      .filter { "/src/main/" in it.invariantSeparatorsPath }
      .filterNot { "/feature/reddit/" in it.invariantSeparatorsPath }
      .forEach { file ->
        val source = file.readText()
        forbiddenImports.forEach { forbiddenImport ->
          assertFalse(
            "${file.relativeTo(repositoryRoot)} must consume RedditSourceBoundary instead of $forbiddenImport",
            forbiddenImport in source,
          )
        }
      }
  }

  @Test
  fun `Settings dataはSummary promptのdata実装を所有しない`() {
    val settingsDataBuild = source("feature/settings/data/build.gradle.kts")
    val settingsData = source(
      "feature/settings/data/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/data/DefaultAiModelRepository.kt",
    )
    val modelContract = source(
      "feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt",
    )
    val routeComposition = source("app/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt")

    assertFalse("Settings data must not depend on Summary data", ":feature:summary:data" in settingsDataBuild)
    assertFalse("AI model repository must not construct SummaryPromptStore", "SummaryPromptStore" in settingsData)
    assertFalse("AI model contract must not own Summary prompt state", "summaryPrompt" in modelContract)
    assertTrue(
      "Settings route must receive the Summary-owned prompt capability",
      "summaryPromptSettings = container.summaryPromptSettings" in routeComposition,
    )
  }

  @Test
  fun `generic feature runtime graphはRouteとWorkerへ露出しない`() {
    listOf(
      "app/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt",
      "app/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt",
      "app/src/main/java/dev/terashima/yomitorirss/AppCrossFeatureRuntimeDependencies.kt",
    ).forEach { path ->
      assertFalse(
        "$path must use narrow AppContainer capabilities instead of the generic feature runtime graph",
        "featureRuntimeDependencies" in source(path),
      )
    }
  }

  @Test
  fun `mainのsigned APK buildはAndroid quality checks成功後だけ実行する`() {
    val workflow = source(".github/workflows/build-apk.yml")
    val qualityChecks = workflow.substringAfter("  quality_checks:\n").substringBefore("\n  quality:\n")
    val build = workflow.substringAfter("  build:\n")

    assertTrue(
      "Architecture/Test/Lint matrix must run for main",
      "github.ref == 'refs/heads/main'" in qualityChecks,
    )
    assertTrue(
      "signed APK build must depend on the Android quality matrix",
      "needs:\n      - quality_checks" in build,
    )
  }

  @Test
  fun `external version identifiersはBuildConfig versionを正本にする`() {
    val container = source("app/src/main/java/dev/terashima/yomitorirss/AppContainer.kt")
    val network = source(
      "core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClient.kt",
    )
    val workflow = source(".github/workflows/build-apk.yml")

    assertTrue(
      "application User-Agent must derive the version from BuildConfig",
      "Mosaic/${'$'}{BuildConfig.VERSION_NAME} (Android)" in container,
    )
    assertFalse("core network must not hardcode the app version", "Mosaic/0.2" in network)
    assertFalse("APK workflow must not hardcode the app version", "mosaic-0.2.0-arm64-v8a.apk" in workflow)
    assertTrue("APK workflow must read output metadata versionName", "[\"versionName\"]" in workflow)
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
