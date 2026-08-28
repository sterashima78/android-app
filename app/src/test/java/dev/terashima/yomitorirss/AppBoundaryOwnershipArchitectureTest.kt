package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBoundaryOwnershipArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  private val compositionSourceRoot = "app/composition/src/main/java/dev/terashima/yomitorirss"

  @Test
  fun `Workout AI advisorはWorkout dataが所有する`() {
    val advisorPath = "feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutAiAdvisor.kt"
    val advisor = source(advisorPath)
    val workoutBuild = source("feature/workout/data/build.gradle.kts")
    val routeComposition = source("$compositionSourceRoot/AppSupportingRouteDependencies.kt")

    assertTrue("Workout data must own the AI advisor", File(repositoryRoot, advisorPath).isFile)
    assertFalse(
      "app must not own Workout AI policy",
      File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppWorkoutAiAdvisor.kt").exists(),
    )
    assertTrue("Workout data must depend on provider-neutral inference", ":core:ai-inference" in workoutBuild)
    assertTrue("Workout advisor must implement the feature contract", "WorkoutAiAdvisor" in advisor)
    assertTrue("app composition must only compose the Workout-owned advisor", "DefaultWorkoutAiAdvisor(" in routeComposition)
  }

  @Test
  fun `provider neutral ChatGPT text inferenceはOpenAI coreが所有する`() {
    val adapterPath = "core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptTextInference.kt"
    val adapter = source(adapterPath)
    val cloudBuild = source("core/ai-cloud-openai/build.gradle.kts")
    val aiComposition = source("$compositionSourceRoot/AppAiCoreRuntimeDependencies.kt")

    assertTrue("OpenAI core must own ChatGptTextInference", File(repositoryRoot, adapterPath).isFile)
    assertFalse(
      "app must not own provider technical text inference",
      File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/ChatGptTextInference.kt").exists(),
    )
    assertTrue("OpenAI core must implement the provider-neutral contract", "AiTextInference" in adapter)
    assertTrue("OpenAI core must depend on ai-inference", ":core:ai-inference" in cloudBuild)
    assertTrue(
      "app composition must consume the provider-owned adapter",
      "dev.terashima.yomitorirss.core.aicloudopenai.ChatGptTextInference" in aiComposition,
    )
  }

  @Test
  fun `Activity result authorization bridgeはplatform packageが所有する`() {
    val authorizationPath = "$compositionSourceRoot/platform/authorization/AuthorizationDependencies.kt"
    val authorization = source(authorizationPath)
    val mailHost = source("app/src/main/java/dev/terashima/yomitorirss/ui/MailRouteHost.kt")
    val libraryHost = source("app/src/main/java/dev/terashima/yomitorirss/ui/LibraryRoute.kt")

    assertTrue("platform package must own authorization bridges", File(repositoryRoot, authorizationPath).isFile)
    assertFalse(
      "root app package must not keep authorization bridge declarations",
      File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppAuthorizationDependencies.kt").exists(),
    )
    assertTrue("authorization bridge must own Mail activity-result boundary", "MailAuthorizationDependencies" in authorization)
    assertTrue(
      "Mail host must import the platform authorization boundary",
      "dev.terashima.yomitorirss.platform.authorization.MailAuthorizationOutcome" in mailHost,
    )
    assertTrue(
      "Library host must import the platform authorization boundary",
      "dev.terashima.yomitorirss.platform.authorization.LibraryAuthorizationOutcome" in libraryHost,
    )
  }

  @Test
  fun `startup background compositionはbackground packageが所有する`() {
    val backgroundPath = "$compositionSourceRoot/composition/background/AppBackgroundRuntime.kt"
    val backgroundRuntime = source(backgroundPath)
    val appContainer = source("$compositionSourceRoot/AppContainer.kt")

    assertTrue("background package must own startup composition", File(repositoryRoot, backgroundPath).isFile)
    assertFalse(
      "composition root must not keep startup background runtime",
      File(repositoryRoot, "$compositionSourceRoot/AppBackgroundRuntime.kt").exists(),
    )
    assertTrue(
      "AppContainer must import the packaged background runtime",
      "dev.terashima.yomitorirss.composition.background.AppBackgroundRuntime" in appContainer,
    )
    assertTrue("background runtime must keep startup scheduling", "BookmarkAutoEnrichmentBackfillScheduler.schedule" in backgroundRuntime)
  }

  @Test
  fun `LAN Web server service manifestはWeb dataが所有する`() {
    val appManifest = source("app/src/main/AndroidManifest.xml")
    val webDataManifest = source("feature/web/data/src/main/AndroidManifest.xml")
    val serviceName = "dev.terashima.yomitorirss.feature.web.data.LanWebServerService"

    assertFalse("app manifest must not own the LAN Web Server service", serviceName in appManifest)
    assertTrue("Web data manifest must own the LAN Web Server service", serviceName in webDataManifest)
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
