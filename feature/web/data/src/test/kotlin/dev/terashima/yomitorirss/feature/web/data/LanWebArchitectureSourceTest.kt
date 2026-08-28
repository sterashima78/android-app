package dev.terashima.yomitorirss.feature.web.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanWebArchitectureSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "feature").isDirectory }
      ?: error("repository root not found")
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

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
