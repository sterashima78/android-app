package dev.terashima.yomitorirss.feature.knowledge.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class KnowledgeApplicationServiceArchitectureTest {
  @Test
  fun `Knowledge RepositoryはAI生成やcross-context source収集を所有しない`() {
    val source = repositoryFile(
      "feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/DefaultKnowledgeRepository.kt",
    ).readText()

    assertFalse(source.contains("LocalModelManager"))
    assertFalse(source.contains("BookmarkReader"))
    assertFalse(source.contains("SummaryReader"))
    assertFalse(source.contains("rawQuery("))
  }

  @Test
  fun `Knowledge generation serviceはSQLを直接操作しない`() {
    val source = repositoryFile(
      "feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/DefaultKnowledgeGenerationService.kt",
    ).readText()

    assertFalse(source.contains("DatabaseConnection"))
    assertFalse(source.contains("rawQuery("))
    assertFalse(source.contains("ContentValues"))
  }

  private fun repositoryFile(path: String): File {
    val start = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(start) { current -> current.parentFile }
      .map { root -> File(root, path) }
      .firstOrNull(File::isFile)
      ?: error("Repository source file not found: $path (start=$start)")
  }
}
