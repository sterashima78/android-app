package dev.terashima.yomitorirss.composition

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCompositionCleanupSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  private val compositionSourceRoot = "app/composition/src/main/java/dev/terashima/yomitorirss"

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

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
