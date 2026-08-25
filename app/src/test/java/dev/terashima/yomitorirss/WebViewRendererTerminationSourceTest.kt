package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewRendererTerminationSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `productionのWebViewClientはrenderer終了を処理する`() {
    val webViewSources = repositoryRoot
      .walkTopDown()
      .filter(File::isFile)
      .filter { it.extension == "kt" }
      .filter { "/src/main/" in "/${it.relativeTo(repositoryRoot).invariantSeparatorsPath}" }
      .mapNotNull { file ->
        val source = file.readText()
        if (source.contains("WebView(") && source.contains("webViewClient")) {
          file to source
        } else {
          null
        }
      }
      .toList()

    assertTrue("production WebView source was not found", webViewSources.isNotEmpty())

    val missingHandlers = webViewSources
      .filterNot { (_, source) -> source.contains("onRenderProcessGone") }
      .map { (file, _) -> file.relativeTo(repositoryRoot).invariantSeparatorsPath }

    assertTrue(
      "onRenderProcessGone is missing: ${missingHandlers.joinToString()}",
      missingHandlers.isEmpty(),
    )
  }
}
