package dev.terashima.yomitorirss

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
        "SettingsRoute must not own Settings or cross-feature presentation: $presentationMarker",
        presentationMarker in source,
      )
    }
    assertTrue(
      "SettingsRoute must keep Android document activity result wiring",
      "rememberLauncherForActivityResult(" in source,
    )
  }

  @Test
  fun `Settings固有overlayはsettings featureが所有する`() {
    val appHost = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/SettingsFeatureHost.kt",
    ).readText()
    val featureScreen = File(
      repositoryRoot,
      "feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SettingsScreen.kt",
    ).readText()

    listOf(
      "ModelManagerDialog(",
      "ChatGptDebugDialog(",
      "AiExecutionSettingsScreen(",
    ).forEach { settingsPresentation ->
      assertFalse(
        "app SettingsFeatureHost must not own Settings-local presentation: $settingsPresentation",
        settingsPresentation in appHost,
      )
      assertTrue(
        "Settings feature must own its local presentation: $settingsPresentation",
        settingsPresentation in featureScreen,
      )
    }
    assertTrue(
      "Settings feature must own local overlay state",
      "SettingsLocalOverlay" in featureScreen,
    )
  }

  @Test
  fun `Settings app hostはcross feature overlayだけを合成する`() {
    val source = File(
      repositoryRoot,
      "app/src/main/java/dev/terashima/yomitorirss/ui/SettingsFeatureHost.kt",
    ).readText()

    listOf(
      "SummaryPromptDialog(",
      "AiTaskQueueRoute(",
      "GoogleDriveBackupDialog(",
    ).forEach { crossFeaturePresentation ->
      assertTrue(
        "SettingsFeatureHost must keep cross-feature composition: $crossFeaturePresentation",
        crossFeaturePresentation in source,
      )
    }
    assertTrue(
      "SettingsFeatureHost state must explicitly represent cross-feature overlays",
      "SettingsCrossFeatureOverlay" in source,
    )
    assertFalse(
      "SettingsFeatureHost must not own Android activity-result wiring",
      "rememberLauncherForActivityResult(" in source,
    )
  }
}
