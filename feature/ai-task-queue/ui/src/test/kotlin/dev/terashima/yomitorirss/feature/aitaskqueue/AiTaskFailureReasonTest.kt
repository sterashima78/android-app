package dev.terashima.yomitorirss.feature.aitaskqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiTaskFailureReasonTest {
  @Test
  fun `失敗タスクは保存された失敗理由を表示する`() {
    val item = task(
      state = AiTaskQueueItemState.FAILED,
      error = "  モデル出力を解析できませんでした  ",
    )

    assertEquals("モデル出力を解析できませんでした", aiTaskFailureReason(item))
  }

  @Test
  fun `失敗理由が未記録でも理由欄を空にしない`() {
    val item = task(state = AiTaskQueueItemState.FAILED, error = null)

    assertEquals("詳細な失敗理由は記録されていません", aiTaskFailureReason(item))
  }

  @Test
  fun `失敗以外のタスクには失敗理由を表示しない`() {
    val item = task(
      state = AiTaskQueueItemState.QUEUED,
      error = "過去のエラー",
    )

    assertNull(aiTaskFailureReason(item))
  }

  private fun task(
    state: AiTaskQueueItemState,
    error: String?,
  ) = AiTaskQueueItem(
    id = "test-task",
    kind = AiTaskQueueItemKind.SUMMARY,
    title = "架空の記事",
    source = "テスト",
    state = state,
    error = error,
  )
}
