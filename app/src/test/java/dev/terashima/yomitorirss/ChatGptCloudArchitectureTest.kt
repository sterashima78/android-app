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

  @Test fun `ChatGPT protocol details stay inside the OpenAI cloud adapter`() {
    val endpointMarker = "chatgpt.com/backend-api"
    repositoryRoot.walkTopDown().filter(File::isFile).filter { it.extension == "kt" }.filter { "/src/main/" in it.invariantSeparatorsPath }
      .filterNot { "/core/ai-cloud-openai/" in it.invariantSeparatorsPath }.forEach { file ->
        assertFalse("${file.relativeTo(repositoryRoot)} must not own the ChatGPT backend endpoint", endpointMarker in file.readText())
      }
  }

  @Test fun `OpenAI dependency is limited to cloud feature infrastructure`() {
    listOf("feature/summary", "feature/knowledge").forEach { relativeRoot ->
      File(repositoryRoot, relativeRoot).walkTopDown().filter(File::isFile).filter { it.extension == "kt" }.filter { "/src/main/" in it.invariantSeparatorsPath }
        .filterNot { "/data/" in it.invariantSeparatorsPath }.forEach { file ->
          assertFalse("${file.relativeTo(repositoryRoot)} must not depend on core ai-cloud-openai", "dev.terashima.yomitorirss.core.aicloudopenai" in file.readText())
        }
    }
  }

  @Test fun `Feature cloud policy adapters are owned by feature data`() {
    val summaryAdapter = source("feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/ChatGptSummaryCloudInference.kt")
    assertTrue("Summary adapter must implement SummaryCloudInference", "SummaryCloudInference" in summaryAdapter)
    assertTrue("Summary adapter must consume normalized inference client", "ChatGptInferenceClient" in summaryAdapter)
    assertFalse("Summary adapter must not parse provider HTTP status", "HTTP_STATUS_PATTERN" in summaryAdapter)
    val knowledgeAdapter = source("feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/ChatGptKnowledgeTextInference.kt")
    assertTrue("Knowledge adapter must implement AiTextInference", "AiTextInference" in knowledgeAdapter)
    assertTrue("Knowledge adapter must consume normalized inference client", "ChatGptInferenceClient" in knowledgeAdapter)
    assertFalse("Knowledge adapter must not parse provider HTTP status", "HTTP_STATUS_PATTERN" in knowledgeAdapter)
    assertFalse("composition must not own Summary provider policy", File(repositoryRoot, "app/composition/src/main/java/dev/terashima/yomitorirss/ChatGptSummaryCloudInference.kt").exists())
    assertFalse("composition must not own Knowledge provider policy", File(repositoryRoot, "app/composition/src/main/java/dev/terashima/yomitorirss/ChatGptKnowledgeTextInference.kt").exists())
  }

  @Test fun `ChatGPT OAuth credentials are excluded from Android backup surfaces`() {
    val backupRules = source("app/src/main/res/xml/backup_rules.xml"); val extractionRules = source("app/src/main/res/xml/data_extraction_rules.xml")
    val oauthInclude = "<include domain=\"sharedpref\" path=\"chatgpt_oauth_secure.xml\""; val wildcard = "<include domain=\"sharedpref\" path=\".\""
    assertFalse(oauthInclude in backupRules); assertFalse(oauthInclude in extractionRules); assertFalse(wildcard in backupRules); assertFalse(wildcard in extractionRules)
  }

  @Test fun `Settings debug UI never renders OAuth token field names`() {
    val debugUi = source("feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugDialog.kt")
    assertFalse("access_token" in debugUi); assertFalse("refresh_token" in debugUi); assertFalse("Authorization" in debugUi)
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
