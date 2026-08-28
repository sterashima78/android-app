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

  private val compositionSourceRoot = "app/composition/src/main/java/dev/terashima/yomitorirss"

  @Test
  fun `App route compositionはRedditの低レベル分類規則を再実装しない`() {
    val routeComposition = source("$compositionSourceRoot/composition/route/AppContentRouteDependencies.kt")

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
    val routeComposition = source("$compositionSourceRoot/composition/route/AppSupportingRouteDependencies.kt")

    assertFalse("Settings data must not depend on Summary data", ":feature:summary:data" in settingsDataBuild)
    assertFalse("AI model repository must not construct SummaryPromptStore", "SummaryPromptStore" in settingsData)
    assertFalse("AI model contract must not own Summary prompt state", "summaryPrompt" in modelContract)
    assertTrue(
      "Settings route must receive the Summary-owned prompt capability",
      "summaryPromptSettings = container.summaryPromptSettings" in routeComposition,
    )
  }

  @Test
  fun `単発テキスト生成featureはprovider neutral contractを利用する`() {
    val summaryBuild = source("feature/summary/data/build.gradle.kts")
    val knowledgeBuild = source("feature/knowledge/data/build.gradle.kts")
    val libraryOrganization = source(
      "feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryOrganizationSuggester.kt",
    )
    val aiCore = source("$compositionSourceRoot/composition/ai/AppAiCoreRuntimeDependencies.kt")
    val libraryRuntime = source("$compositionSourceRoot/composition/library/AppLibraryRuntimeDependencies.kt")
    val knowledgeRuntime = source("$compositionSourceRoot/composition/knowledge/AppKnowledgeRuntimeDependencies.kt")
    val workerFactory = source("$compositionSourceRoot/AppWorkerFactory.kt")

    assertTrue("Summary data must depend on ai-inference", ":core:ai-inference" in summaryBuild)
    assertFalse("Summary data must not depend on local ai-runtime", ":core:ai-runtime" in summaryBuild)
    assertTrue("Knowledge data must depend on ai-inference", ":core:ai-inference" in knowledgeBuild)
    assertFalse("Knowledge data must not depend on local ai-runtime", ":core:ai-runtime" in knowledgeBuild)
    assertTrue("Library organization must consume AiTextInference", "AiTextInference" in libraryOrganization)
    assertFalse("Library organization must not consume LocalModelManager", "LocalModelManager" in libraryOrganization)
    assertTrue(
      "app AI core must compose the process-isolated local adapter once",
      "ProcessIsolatedLocalAiTextInference(application, modelManager)" in aiCore,
    )
    assertTrue("library composition must inject text inference", "textInferenceProvider" in libraryRuntime)
    assertTrue("knowledge composition must inject text inference", "textInference" in knowledgeRuntime)
    assertTrue("summary workers must receive text inference", "textInferenceProvider" in workerFactory)
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
    val facade = source("$compositionSourceRoot/AppRouteDependencies.kt")

    assertTrue("route facade must delegate content composition", "AppContentRouteDependencies" in facade)
    assertTrue("route facade must delegate supporting composition", "AppSupportingRouteDependencies" in facade)
    assertFalse("route facade must not construct feature factories", ".Factory(" in facade)
    assertFalse("route facade must not construct repositories", "Repository(" in facade)
  }

  @Test
  fun `generic feature runtime graphはRouteとWorkerへ露出しない`() {
    listOf(
      "$compositionSourceRoot/AppRouteDependencies.kt",
      "$compositionSourceRoot/composition/route/AppContentRouteDependencies.kt",
      "$compositionSourceRoot/composition/route/AppSupportingRouteDependencies.kt",
      "$compositionSourceRoot/AppWorkerFactory.kt",
      "$compositionSourceRoot/composition/crossfeature/AppCrossFeatureRuntimeDependencies.kt",
    ).forEach { path ->
      assertFalse(
        "$path must use narrow AppContainer capabilities instead of a generic feature runtime graph",
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
  fun `共有dependency versionはcatalogを正本にする`() {
    val catalog = source("gradle/libs.versions.toml")
    val settingsData = source("feature/settings/data/build.gradle.kts")
    val chatData = source("feature/chat/data/build.gradle.kts")

    assertTrue("catalog must own the coroutines version", "kotlinx-coroutines = \"1.11.0\"" in catalog)
    assertTrue("catalog must own the serialization version", "kotlinx-serialization = \"1.11.0\"" in catalog)
    assertTrue("catalog must own the JUnit4 version", "junit4 = \"4.13.2\"" in catalog)

    assertTrue("Settings data must use the coroutines catalog alias", "libs.kotlinx.coroutines.android" in settingsData)
    assertFalse("Settings data must not hardcode the coroutines version", "kotlinx-coroutines-android:1.11.0" in settingsData)

    assertTrue("Chat data must use the coroutines catalog alias", "libs.kotlinx.coroutines.android" in chatData)
    assertTrue("Chat data must use the serialization catalog alias", "libs.kotlinx.serialization.json" in chatData)
    assertTrue("Chat data must use the JUnit catalog alias", "libs.junit4" in chatData)
    assertFalse("Chat data must not hardcode migrated dependency versions", ":1.11.0\"" in chatData)
    assertFalse("Chat data must not hardcode JUnit4 version", "junit:junit:4.13.2" in chatData)
  }

  @Test
  fun `PR quality checksとmain signed APK buildはworkflowを分離する`() {
    val checkWorkflow = source(".github/workflows/check.yml")
    val buildWorkflow = source(".github/workflows/build.yml")

    listOf("Public repository", "Architecture", "Test", "Lint").forEach { checkName ->
      assertTrue("PR workflow must expose the required check: $checkName", "name: $checkName" in checkWorkflow)
    }
    assertTrue(
      "Architecture check must include ADR integrity verification",
      "python3 scripts/verify_adr_integrity.py" in checkWorkflow,
    )
    assertFalse("PR check workflow must not assemble the release APK", ":app:assembleRelease" in checkWorkflow)
    assertTrue("main build workflow must assemble the signed release APK", ":app:assembleRelease" in buildWorkflow)
    assertFalse("main build workflow must not rerun the PR quality matrix", "matrix:" in buildWorkflow)
  }

  @Test
  fun `external version identifiersはBuildConfig versionを正本にする`() {
    val application = source("app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt")
    val container = source("$compositionSourceRoot/AppContainer.kt")
    val network = source(
      "core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClient.kt",
    )
    val workflow = source(".github/workflows/build.yml")

    assertTrue(
      "application must pass BuildConfig version across the composition boundary",
      "appVersionName = BuildConfig.VERSION_NAME" in application,
    )
    assertTrue(
      "application User-Agent must derive the version from the injected app version",
      "Mosaic/${'$'}appVersionName (Android)" in container,
    )
    assertFalse("composition module must not depend on app BuildConfig", "BuildConfig.VERSION_NAME" in container)
    assertFalse("core network must not hardcode the app version", "Mosaic/0.2" in network)
    assertFalse("APK workflow must not hardcode the app version", "mosaic-0.2.0-arm64-v8a.apk" in workflow)
    assertTrue("APK workflow must read output metadata versionName", "[\"versionName\"]" in workflow)
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
