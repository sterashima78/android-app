package dev.terashima.yomitorirss.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTaskQueueViewModelTest {
  @Test
  fun `完了済みを除外して実行中_待機中_一時停止の順に並べる`() {
    val tasks = listOf(
      task("paused-1", AiTaskQueueItemState.PAUSED),
      task("completed", AiTaskQueueItemState.COMPLETED),
      task("queued-1", AiTaskQueueItemState.QUEUED),
      task("running-1", AiTaskQueueItemState.RUNNING),
      task("failed", AiTaskQueueItemState.FAILED),
      task("paused-2", AiTaskQueueItemState.PAUSED),
      task("running-2", AiTaskQueueItemState.RUNNING),
      task("queued-2", AiTaskQueueItemState.QUEUED),
    )

    val result = prepareVisibleAiTasks(tasks)

    assertEquals(
      listOf(
        "running-1",
        "running-2",
        "queued-1",
        "queued-2",
        "paused-1",
        "paused-2",
        "failed",
      ),
      result.map(AiTaskQueueItem::id),
    )
  }

  @Test
  fun `完了済みだけなら表示対象は空になる`() {
    val tasks = listOf(
      task("completed-1", AiTaskQueueItemState.COMPLETED),
      task("completed-2", AiTaskQueueItemState.COMPLETED),
    )

    assertEquals(emptyList<AiTaskQueueItem>(), prepareVisibleAiTasks(tasks))
  }

  private fun task(id: String, state: AiTaskQueueItemState) = AiTaskQueueItem(
    id = id,
    kind = AiTaskQueueItemKind.SUMMARY,
    title = id,
    source = "test",
    state = state,
  )
}
