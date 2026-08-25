package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptCloudArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `ChatGPT protocol details stay inside the OpenAI cloud adapter`() {
    val endpointMarker = "chatgpt.com/backend-api"
    repositoryRoot.walkTopDown()
      .filter(File::isFile)
      .filter { it.extension == "kt" }
      .filter { "/src/main/" in it.invariantSeparatorsPath }
      .filterNot { "/core/ai-cloud-openai/" in it.invariantSeparatorsPath }
      .forEach { file ->
        assertFalse(
          "${file.relativeTo(repositoryRoot)} must not own the ChatGPT backend endpoint",
          endpointMarker in file.readText(),
        )
      }
  }

  @Test
  fun `ChatGPT OAuth credentials are excluded from Android backup surfaces`() {
    val backupRules = source("app/src/main/res/xml/backup_rules.xml")
    val extractionRules = source("app/src/main/res/xml/data_extraction_rules.xml")
    assertTrue("full backup must exclude ChatGPT OAuth storage", "chatgpt_oauth_secure.xml" in backupRules)
    assertTrue("data extraction must exclude ChatGPT OAuth storage", "chatgpt_oauth_secure.xml" in extractionRules)
  }

  @Test
  fun `Settings debug UI never renders OAuth token field names`() {
    val debugUi = source(
      "feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugDialog.kt",
    )
    assertFalse("debug UI must not render access tokens", "access_token" in debugUi)
    assertFalse("debug UI must not render refresh tokens", "refresh_token" in debugUi)
    assertFalse("debug UI must not render Authorization headers", "Authorization" in debugUi)
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
