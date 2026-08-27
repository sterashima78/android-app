package dev.terashima.yomitorirss

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AndroidBackupRulesArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `platform backupはファイル全体を許可できる設定だけを列挙する`() {
    val legacyRules = sharedPreferenceIncludes("app/src/main/res/xml/backup_rules.xml")
    val extractionRules = extractionSharedPreferenceIncludes()

    assertEquals(SAFE_PREFERENCES, legacyRules)
    assertEquals(SAFE_PREFERENCES, extractionRules.getValue("cloud-backup"))
    assertEquals(SAFE_PREFERENCES, extractionRules.getValue("device-transfer"))
    assertFalse("包括指定では新しい設定が自動的に対象になる", "." in legacyRules)
  }

  @Test
  fun `機密情報と端末依存情報とモデル管理情報を対象にしない`() {
    val allIncludes = buildSet {
      addAll(sharedPreferenceIncludes("app/src/main/res/xml/backup_rules.xml"))
      extractionSharedPreferenceIncludes().values.forEach(::addAll)
    }

    assertEquals(emptySet<String>(), allIncludes intersect FORBIDDEN_PREFERENCES)
  }

  @Test
  fun `local model artifactはdevice transferで維持する`() {
    val legacyFileIncludes = includes("app/src/main/res/xml/backup_rules.xml", "file")
    val extractionFileIncludes = extractionIncludes("file")

    assertTrue(LOCAL_MODEL_DIRECTORY in legacyFileIncludes)
    assertFalse(LOCAL_MODEL_DIRECTORY in extractionFileIncludes.getValue("cloud-backup"))
    assertTrue(LOCAL_MODEL_DIRECTORY in extractionFileIncludes.getValue("device-transfer"))
  }

  @Test
  fun `platform backupの許可リストはBackupPreferencesと意図した差だけを持つ`() {
    val source = File(
      repositoryRoot,
      "feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupPreferences.kt",
    ).readText()
    val archiveRules = Regex("""PreferenceBackupRule\(\s*(?:name\s*=\s*)?"([^"]+)"""")
      .findAll(source)
      .map { "${it.groupValues[1]}.xml" }
      .toSet()

    assertEquals(SAFE_PREFERENCES + KEY_FILTERED_ARCHIVE_ONLY_PREFERENCES, archiveRules)
    assertEquals(KEY_FILTERED_ARCHIVE_ONLY_PREFERENCES, archiveRules - SAFE_PREFERENCES)
  }

  private fun sharedPreferenceIncludes(path: String): Set<String> = includes(path, "sharedpref")

  private fun includes(path: String, domain: String): Set<String> =
    parse(path).documentElement.childElements("include")
      .filter { it.getAttribute("domain") == domain }
      .mapTo(linkedSetOf()) { it.getAttribute("path") }

  private fun extractionSharedPreferenceIncludes(): Map<String, Set<String>> = extractionIncludes("sharedpref")

  private fun extractionIncludes(domain: String): Map<String, Set<String>> =
    parse("app/src/main/res/xml/data_extraction_rules.xml").documentElement
      .childElements()
      .associate { section ->
        section.tagName to section.childElements("include")
          .filter { it.getAttribute("domain") == domain }
          .mapTo(linkedSetOf()) { it.getAttribute("path") }
      }

  private fun parse(path: String) = DocumentBuilderFactory.newInstance()
    .newDocumentBuilder()
    .parse(File(repositoryRoot, path))

  private fun Element.childElements(tagName: String? = null): List<Element> =
    (0 until childNodes.length)
      .mapNotNull { childNodes.item(it) as? Element }
      .filter { tagName == null || it.tagName == tagName }

  private companion object {
    const val LOCAL_MODEL_DIRECTORY = "local-summary-models/"
    val SAFE_PREFERENCES = setOf(
      "background_data_fetch.xml",
      "book_reader_position.xml",
      "local_ai_background_execution.xml",
      "summary_preferences.xml",
      "workout.xml",
      "workout_ai.xml",
      "x_viewer_preferences.xml",
    )
    val KEY_FILTERED_ARCHIVE_ONLY_PREFERENCES = setOf(
      "library_ai_preferences.xml",
      "local_summary_models.xml",
    )
    val FORBIDDEN_PREFERENCES = setOf(
      "chatgpt_oauth_secure.xml",
      "smb_library_credentials.xml",
      "google_drive_backup.xml",
      "lan_web_server.xml",
      "local_context_benchmarks.xml",
      "local_summary_models.xml",
    )
  }
}
