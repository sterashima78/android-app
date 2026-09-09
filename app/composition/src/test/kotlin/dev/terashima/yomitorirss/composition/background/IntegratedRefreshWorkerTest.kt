package dev.terashima.yomitorirss.composition.background

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedRefreshWorkerTest {
  @Test
  fun `更新後に追加された統合ビュー未読だけを新着として扱う`() {
    val before = setOf("article:a", "mail:c")
    val after = setOf("article:a", "article:d", "mail:e")

    assertEquals(setOf("article:d", "mail:e"), newUnreadKeys(before, after))
  }

  @Test
  fun `既存未読だけなら新着通知対象は空になる`() {
    val before = setOf("article:a", "mail:b")
    val after = setOf("article:a", "mail:b")

    assertEquals(emptySet<String>(), newUnreadKeys(before, after))
  }

  @Test
  fun `動画providerは周期更新するが統合ビュー未読snapshotには含めない`() {
    val source = repositoryFile(
      "app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/IntegratedRefreshWorker.kt",
    ).readText()

    assertTrue(source.contains("container.videoProviderRepository.refreshProviders()"))
    assertFalse(source.contains("container.videoProviderRepository.unreadVideos()"))
  }

  @Test
  fun `同期前snapshotを取得できない場合は新着通知対象を作らない`() {
    assertEquals(emptySet<String>(), newUnreadKeys(null, setOf("article:a")))
  }

  @Test
  fun `同期後snapshotを取得できない場合は新着通知対象を作らない`() {
    assertEquals(emptySet<String>(), newUnreadKeys(setOf("article:a"), null))
  }

  private fun repositoryFile(path: String): File {
    val start = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(start) { current -> current.parentFile }
      .map { root -> File(root, path) }
      .firstOrNull(File::isFile)
      ?: error("Repository source file not found: $path (start=$start)")
  }
}
