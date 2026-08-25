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
  fun `App route compositionはRedditの低レベル分類規則を再実装しない`() {
    val routeComposition = source(
      "app/src/main/java/dev/terashima/yomitorirss/AppContentRouteDependencies.kt",
    )

    assertTrue("route composition must consume the Reddit-owned boundary", "RedditSourceBoundary" in routeComposition)
    listOf(
      "isRedditArticle",
      "isRedditFeedUrl",
      "redditCommunityFeedUrl",
      "redditThreadId",
    ).forEach { lowLevelRule ->
      assertFalse(
        "route composition must not depend on Reddit low-level rule: $lowLevelRule",
        "feature.reddit.$lowLevelRule" in routeComposition,
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
        val fileSource = file.readText()
        forbiddenImports.forEach { forbiddenImport ->
          assertFalse(
            "${file.relativeTo(repositoryRoot)} must consume RedditSourceBoundary instead of $forbiddenImport",
            forbiddenImport in fileSource,
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
    val routeComposition = source(
      "app/src/main/java/dev/terashima/yomitorirss/AppSupportingRouteDependencies.kt",
    )

    assertFalse("Settings data must not depend on Summary data", ":feature:summary:data" in settingsDataBuild)
    assertFalse("AI model repository must not construct SummaryPromptStore", "SummaryPromptStore" in settingsData)
    assertFalse("AI model contract must not own Summary prompt state", "summaryPrompt" in modelContract)
    assertTrue(
      "Settings route must receive the Summary-owned prompt capability",
      "summaryPromptSettings = container.summaryPromptSettings" in routeComposition,
    )
  }

  @Test
  fun `LAN Web serverはtransport read model rendererを分離する`() {
    val server = source(
      "feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebServer.kt",
    )
    val readModel = source(
      "feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebReadModel.kt",
    )
    val renderer = source(
      "feature/web/data/src/main/kotlin/dev/terashima/yomitorirss/feature/web/data/LanWebRenderer.kt",
    )

    assertTrue("transport must delegate repository reads", "LanWebReadModel" in server)
    assertTrue("transport must delegate HTML rendering", "LanWebRenderer" in server)
    assertFalse("transport must not query article repositories", "listUnreadArticles(" in server)
    assertFalse("transport must not own page markup", "<!doctype html>" in server)
    assertTrue("read model must own repository reads", "listUnreadArticles(" in readModel)
    assertTrue("renderer must own page markup", "<!doctype html>" in renderer)
  }

  @Test
  fun `Web data test sourceはpackageと物理パスを一致させる`() {
    val sourceRoot = File(repositoryRoot, "feature/web/data/src/test/kotlin")
    sourceRoot.walkTopDown()
      .filter(File::isFile)
      .filter { it.extension == "kt" }
      .forEach { file ->
        val packageName = Regex("(?m)^package\\s+([A-Za-z0-9_.]+)\\s*$")
          .find(file.readText())
          ?.groupValues
          ?.get(1)
          ?: error("test source package not found: ${file.relativeTo(repositoryRoot)}")
        val expected = packageName.replace('.', '/') + "/${file.name}"
        val actual = file.relativeTo(sourceRoot).invariantSeparatorsPath
        assertTrue(
          "${file.relativeTo(repositoryRoot)} must live under its declared package path: expected=$expected actual=$actual",
          actual == expected,
        )
      }
  }

  @Test
  fun `AppRouteDependenciesはcontentとsupporting compositionの薄いfaçadeにする`() {
    val facade = source("app/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt")

    assertTrue("route facade must delegate content composition", "AppContentRouteDependencies" in facade)
    assertTrue("route facade must delegate supporting composition", "AppSupportingRouteDependencies" in facade)
    assertFalse("route facade must not construct feature factories", ".Factory(" in facade)
    assertFalse("route facade must not construct repositories", "Repository(" in facade)
  }

  @Test
  fun `generic feature runtime graphはRouteとWorkerへ露出しない`() {
    listOf(
      "app/src/main/java/dev/terashima/yomitorirss/AppRouteDependencies.kt",
      "app/src/main/java/dev/terashima/yomitorirss/AppContentRouteDependencies.kt",
      "app/src/main/java/dev/terashima/yomitorirss/AppSupportingRouteDependencies.kt",
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
  fun `診断artifactはgit管理対象外にする`() {
    val gitignore = source(".gitignore")
    listOf(
      "*.hprof",
      "*.trace",
      "*.perfetto-trace",
      "*.perfetto-trace-unredacted",
      "*.heapprofile",
      "*.heapdump",
      "*.heapsnapshot",
    ).forEach { pattern ->
      assertTrue(".gitignore must exclude diagnostic artifact: $pattern", pattern in gitignore)
    }
  }

  @Test
  fun `mainのsigned APK生成はAndroid quality checks成功後だけ実行する`() {
    val workflow = source(".github/workflows/build-apk.yml")
    val qualityChecks = workflow.substringAfter("  quality_checks:\n").substringBefore("\n  quality:\n")
    val buildJob = workflow.substringAfter("  build:\n")

    assertTrue(
      "Architecture/Test/Lint matrix must run for main",
      "github.ref == 'refs/heads/main'" in qualityChecks,
    )
    assertTrue(
      "signed APK build must depend on the Android quality matrix",
      "needs:\n      - quality_checks" in buildJob,
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
