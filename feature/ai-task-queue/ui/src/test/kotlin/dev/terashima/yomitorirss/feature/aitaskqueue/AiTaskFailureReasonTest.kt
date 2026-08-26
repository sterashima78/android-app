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

    assertEquals(
      AiTaskFailurePresentation(
        label = "失敗理由",
        reason = "モデル出力を解析できませんでした",
      ),
      aiTaskFailurePresentation(item),
    )
  }

  @Test
  fun `失敗理由が未記録でも理由欄を空にしない`() {
    val item = task(state = AiTaskQueueItemState.FAILED, error = null)

    assertEquals(
      AiTaskFailurePresentation(
        label = "失敗理由",
        reason = "詳細な失敗理由は記録されていません",
      ),
      aiTaskFailurePresentation(item),
    )
  }

  @Test
  fun `自動再試行待ちでは直前の失敗理由を表示する`() {
    val item = task(
      state = AiTaskQueueItemState.QUEUED,
      error = "  クラウドAIが一時的に利用できません  ",
    )

    assertEquals(
      AiTaskFailurePresentation(
        label = "直前の失敗・自動再試行待ち",
        reason = "クラウドAIが一時的に利用できません",
      ),
      aiTaskFailurePresentation(item),
    )
  }

  @Test
  fun `通常の待機タスクには失敗理由を表示しない`() {
    val item = task(
      state = AiTaskQueueItemState.QUEUED,
      error = null,
    )

    assertNull(aiTaskFailurePresentation(item))
  }

  @Test
  fun `実行中のタスクに残存エラーがあっても失敗理由を表示しない`() {
    val item = task(
      state = AiTaskQueueItemState.RUNNING,
      error = "過去のエラー",
    )

    assertNull(aiTaskFailurePresentation(item))
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
