package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManifestOwnershipArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `feature owned framework componentはowning moduleのmanifestで宣言する`() {
    val appManifest = File(repositoryRoot, "app/src/main/AndroidManifest.xml").readText()
    val featureComponentDeclarations = Regex(
      "android:name=\\\"dev\\.terashima\\.yomitorirss\\.feature\\.[^\\\"]+\\\"",
    ).findAll(appManifest).map { it.value }.toList()

    assertTrue(
      "executable app manifest must not declare feature-owned framework components: $featureComponentDeclarations",
      featureComponentDeclarations.isEmpty(),
    )
  }
}
