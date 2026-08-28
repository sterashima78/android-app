package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCompositionPackageArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  private val compositionSourceRoot = File(
    repositoryRoot,
    "app/composition/src/main/java/dev/terashima/yomitorirss",
  )

  @Test
  fun `composition rootはexecutable shell向けfacadeだけを公開する`() {
    val allowedRootFiles = setOf(
      "AppContainer.kt",
      "AppDatabaseSchema.kt",
      "AppRouteDependencies.kt",
      "AppWorkerFactory.kt",
    )
    val actualRootFiles = compositionSourceRoot
      .listFiles()
      .orEmpty()
      .filter { it.isFile && it.extension == "kt" }
      .map(File::getName)
      .toSet()

    assertTrue(
      "composition root must contain only the narrow facade files: actual=$actualRootFiles",
      actualRootFiles == allowedRootFiles,
    )
  }

  @Test
  fun `composition implementationは責務packageへ配置する`() {
    val expectedImplementationFiles = listOf(
      "composition/ai/AppAiCoreRuntimeDependencies.kt",
      "composition/background/AppBackgroundRuntime.kt",
      "composition/content/AppContentRuntimeDependencies.kt",
      "composition/crossfeature/AppCrossFeatureRuntimeDependencies.kt",
      "composition/health/AppHealthRuntimeDependencies.kt",
      "composition/knowledge/AppKnowledgeRuntimeDependencies.kt",
      "composition/knowledge/AppKnowledgeTaskRuntimeDependencies.kt",
      "composition/library/AppLibraryRuntimeDependencies.kt",
      "composition/route/AppContentRouteDependencies.kt",
      "composition/route/AppSupportingRouteDependencies.kt",
      "composition/supporting/AppSupportingRuntimeDependencies.kt",
      "platform/authorization/AuthorizationDependencies.kt",
    )

    expectedImplementationFiles.forEach { relativePath ->
      assertTrue(
        "composition responsibility must live in its package: $relativePath",
        File(compositionSourceRoot, relativePath).isFile,
      )
    }
  }

  @Test
  fun `generic feature runtime groupは再導入しない`() {
    assertFalse(
      "mixed AppFeatureRuntimeDependencies must not return to the composition root",
      File(compositionSourceRoot, "AppFeatureRuntimeDependencies.kt").exists(),
    )
    assertFalse(
      "mixed AppFeatureRuntimeDependencies must not be relocated as a catch-all package",
      compositionSourceRoot.walkTopDown()
        .filter(File::isFile)
        .any { it.name == "AppFeatureRuntimeDependencies.kt" },
    )
  }
}
