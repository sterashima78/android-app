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

  private val compositionSourceRoot = "app/composition/src/main/java/dev/terashima/yomitorirss"

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
  fun `Cloud enabled features depend on inference boundaries instead of OpenAI protocol`() {
    listOf("feature/summary", "feature/knowledge").forEach { relativeRoot ->
      val featureRoot = File(repositoryRoot, relativeRoot)
      featureRoot.walkTopDown()
        .filter(File::isFile)
        .filter { it.extension == "kt" }
        .filter { "/src/main/" in it.invariantSeparatorsPath }
        .forEach { file ->
          val source = file.readText()
          assertFalse(
            "${file.relativeTo(repositoryRoot)} must not depend on core ai-cloud-openai",
            "dev.terashima.yomitorirss.core.aicloudopenai" in source,
          )
          assertFalse(
            "${file.relativeTo(repositoryRoot)} must not depend on ChatGptOpenAiClient",
            "ChatGptOpenAiClient" in source,
          )
        }
    }
  }

  @Test
  fun `ChatGPT feature adapters are composed in application composition layer`() {
    val summaryAdapter = source("$compositionSourceRoot/ChatGptSummaryCloudInference.kt")
    assertTrue("composition adapter must implement SummaryCloudInference", "SummaryCloudInference" in summaryAdapter)
    assertTrue("composition adapter must consume normalized inference client", "ChatGptInferenceClient" in summaryAdapter)
    assertFalse("Summary adapter must not parse provider HTTP status", "HTTP_STATUS_PATTERN" in summaryAdapter)
    assertFalse("Summary adapter must not inspect OAuth refresh protocol text", "OAuth token refresh" in summaryAdapter)

    val knowledgeAdapter = source("$compositionSourceRoot/ChatGptKnowledgeTextInference.kt")
    assertTrue("composition adapter must implement AiTextInference", "AiTextInference" in knowledgeAdapter)
    assertTrue("composition adapter must consume normalized inference client", "ChatGptInferenceClient" in knowledgeAdapter)
    assertFalse("Knowledge adapter must not parse provider HTTP status", "HTTP_STATUS_PATTERN" in knowledgeAdapter)
    assertFalse("Knowledge adapter must not inspect OAuth refresh protocol text", "OAuth token refresh" in knowledgeAdapter)
  }

  @Test
  fun `ChatGPT OAuth credentials are excluded from Android backup surfaces`() {
    val backupRules = source("app/src/main/res/xml/backup_rules.xml")
    val extractionRules = source("app/src/main/res/xml/data_extraction_rules.xml")
    val oauthInclude = "<include domain=\"sharedpref\" path=\"chatgpt_oauth_secure.xml\""
    val sharedPreferencesWildcard = "<include domain=\"sharedpref\" path=\".\""

    assertFalse("full backup must not include ChatGPT OAuth storage", oauthInclude in backupRules)
    assertFalse("data extraction must not include ChatGPT OAuth storage", oauthInclude in extractionRules)
    assertFalse("full backup must not include all SharedPreferences", sharedPreferencesWildcard in backupRules)
    assertFalse("data extraction must not include all SharedPreferences", sharedPreferencesWildcard in extractionRules)
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
