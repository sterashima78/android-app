package dev.terashima.yomitoririss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCompositionSourceArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `SettingsRouteはplatform wiringに限定する`() {
    val source = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/SettingsRoute.kt",
    ).readText()

    listOf(
      "mutableStateOf",
      "ModelManagerDialog(",
      "ChatGptDebugDialog(",
      "AiExecutionSettingsScreen(",
      "SummaryPromptDialog(",
      "AiTaskQueueRoute(",
      "GoogleDriveBackupDialog(",
    ).forEach { presentationMarker ->
      assertFalse(
        "SettingsRoute must not own Settings presentation: $presentationMarker",
        presentationMarker in source,
      )
    }
    assertTrue(
      "SettingsRoute must connect the owning Settings feature screen",
      "SettingsFeatureScreen(" in source,
    )
    assertTrue(
      "SettingsRoute must keep Android document activity result wiring",
      "rememberLauncherForActivityResult(" in source,
    )
  }

  @Test
  fun `Settings presentationはsettings featureが所有する`() {
    val featureScreen = File(
      repositoryRoot,
      "feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SettingsScreen.kt",
    ).readText()

    listOf(
      "ModelManagerDialog(",
      "ChatGptDebugDialog(",
      "AiExecutionSettingsScreen(",
      "SummaryPromptDialog(",
      "AiTaskQueueRoute(",
      "GoogleDriveBackupDialog(",
    ).forEach { settingsPresentation ->
      assertTrue(
        "Settings feature must own Settings presentation: $settingsPresentation",
        settingsPresentation in featureScreen,
      )
    }
    assertTrue(
      "Settings feature must own overlay selection state",
      "SettingsOverlay" in featureScreen,
    )
  }

  @Test
  fun `Settings用app presentation bridgeは残さない`() {
    val settingsHost = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/SettingsFeatureHost.kt",
    )
    val featureUiAdapters = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/FeatureUiAdapters.kt",
    )

    assertFalse(
      "SettingsFeatureHost must not reintroduce Settings presentation into app",
      settingsHost.exists(),
    )
    assertFalse(
      "FeatureUiAdapters must not reintroduce feature presentation forwarding into app",
      featureUiAdapters.exists(),
    )
  }
}
