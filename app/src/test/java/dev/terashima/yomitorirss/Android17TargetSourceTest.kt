package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class Android17TargetSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `API37 targetはlocal network permissionを同時に導入する`() {
    val appBuild = source("app/build.gradle.kts")
    val manifest = source("app/src/main/AndroidManifest.xml")
    val webServerHost = source(
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LanWebServerDialogHost.kt",
    )
    val libraryRoute = source(
      "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LibraryRoute.kt",
    )

    assertTrue("app must target API 37", "targetSdk = 37" in appBuild)
    assertTrue(
      "manifest must declare local-network permission",
      "android.permission.ACCESS_LOCAL_NETWORK" in manifest,
    )
    assertTrue(
      "LAN web server must request local-network permission on API 37+",
      "Manifest.permission.ACCESS_LOCAL_NETWORK" in webServerHost &&
        "Build.VERSION.SDK_INT >= 37" in webServerHost,
    )
    assertTrue(
      "SMB library entry must request local-network permission on API 37+",
      "Manifest.permission.ACCESS_LOCAL_NETWORK" in libraryRoute &&
        "Build.VERSION.SDK_INT >= 37" in libraryRoute,
    )
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
