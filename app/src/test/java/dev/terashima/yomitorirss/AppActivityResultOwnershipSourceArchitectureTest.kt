package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppActivityResultOwnershipSourceArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `Compose Activity Result launcherはexecutable appに置かない`() {
    val executableSourceRoot = File(repositoryRoot, "app/src/main/java")
    val unexpected = executableSourceRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .filter { "rememberLauncherForActivityResult" in it.readText() }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .toList()

    assertTrue(
      "Composable Activity Result launchers belong in :app:presentation or owning feature UI: $unexpected",
      unexpected.isEmpty(),
    )
  }

  @Test
  fun `app presentationのCompose Activity Result launcherは監査済みadapterに限定する`() {
    val presentationSourceRoot = File(repositoryRoot, "app/presentation/src/main/kotlin")
    val expectedHosts = setOf(
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppTopBarRoute.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/CalendarRoute.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LanWebServerDialogHost.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LibraryRoute.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/MailRouteHost.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/SettingsRoute.kt",
    )
    val actualHosts = presentationSourceRoot
      .walkTopDown()
      .filter { it.isFile && it.extension == "kt" }
      .filter { "rememberLauncherForActivityResult" in it.readText() }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .toSet()

    assertEquals(
      "app presentation Activity Result ownership changed; review ADR-0205/0208 before changing the host inventory",
      expectedHosts,
      actualHosts,
    )
  }

  @Test
  fun `監査済みapp presentation hostはActivity Result contractを明示する`() {
    val expectedHosts = listOf(
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppTopBarRoute.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/CalendarRoute.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LanWebServerDialogHost.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LibraryRoute.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/MailRouteHost.kt",
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/SettingsRoute.kt",
    )

    expectedHosts.forEach { path ->
      val source = File(repositoryRoot, path).readText()
      assertTrue("audited Activity Result host must use ActivityResultContracts: $path", "ActivityResultContracts." in source)
    }
  }
}
