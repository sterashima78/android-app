package dev.terashima.yomitorirss.feature.aitaskqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun `取得済みタスクから表示件数を計算する`() {
    val counts = countAiTaskQueueTasks(
      listOf(
        task("running", AiTaskQueueItemState.RUNNING),
        task("queued-1", AiTaskQueueItemState.QUEUED),
        task("queued-2", AiTaskQueueItemState.QUEUED),
        task("paused", AiTaskQueueItemState.PAUSED),
        task("stopped", AiTaskQueueItemState.STOPPED),
        task("completed", AiTaskQueueItemState.COMPLETED),
      ),
    )

    assertEquals(AiTaskQueueCounts(running = 1, queued = 2, pausedOrStopped = 2), counts)
  }

  @Test
  fun `同じ状態では高優先度タスクを先に並べる`() {
    val tasks = listOf(
      task("normal", AiTaskQueueItemState.QUEUED, priority = AiTaskQueueItemPriority.NORMAL),
      task("low", AiTaskQueueItemState.QUEUED, priority = AiTaskQueueItemPriority.LOW),
      task("high", AiTaskQueueItemState.QUEUED, priority = AiTaskQueueItemPriority.HIGH),
    )

    assertEquals(
      listOf("high", "normal", "low"),
      prepareVisibleAiTasks(tasks).map(AiTaskQueueItem::id),
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

  @Test
  fun `件数のない実行中フェーズは不定進捗として表示する`() {
    val presentation = aiTaskProgressPresentation(
      task(
        id = "preparing",
        state = AiTaskQueueItemState.RUNNING,
        progressStage = AiTaskQueueProgressStage.PREPARING_MODEL,
      ),
    )

    assertEquals("AIモデルを読み込み中", presentation?.label)
    assertNull(presentation?.fraction)
  }

  @Test
  fun `クラウド要約とメタデータ生成はクラウド実行と分かるラベルを表示する`() {
    val summary = aiTaskProgressPresentation(
      task(
        id = "cloud-summary",
        state = AiTaskQueueItemState.RUNNING,
        progressStage = AiTaskQueueProgressStage.CLOUD_GENERATING,
      ),
    )
    val enrichment = aiTaskProgressPresentation(
      task(
        id = "cloud-enrichment",
        state = AiTaskQueueItemState.RUNNING,
        progressStage = AiTaskQueueProgressStage.CLOUD_ENRICHING,
      ),
    )

    assertEquals("クラウドで記事を要約中", summary?.label)
    assertEquals("クラウドでタグ・フォルダ候補を生成中", enrichment?.label)
  }

  @Test
  fun `長文分割は件数と確定進捗を表示する`() {
    val presentation = aiTaskProgressPresentation(
      task(
        id = "chunk",
        state = AiTaskQueueItemState.RUNNING,
        progressStage = AiTaskQueueProgressStage.PROCESSING_CHUNK,
        progressCurrent = 2,
        progressTotal = 4,
      ),
    )

    assertEquals("長文を分割要約中 2/4", presentation?.label)
    assertEquals(0.5f, presentation?.fraction)
  }

  @Test
  fun `フェーズ情報がない実行中タスクも不定進捗を表示する`() {
    val presentation = aiTaskProgressPresentation(
      task("running", AiTaskQueueItemState.RUNNING),
    )

    assertEquals("AI処理中", presentation?.label)
    assertNull(presentation?.fraction)
  }

  @Test
  fun `待機中タスクには進捗を表示しない`() {
    assertNull(aiTaskProgressPresentation(task("queued", AiTaskQueueItemState.QUEUED)))
  }

  private fun task(
    id: String,
    state: AiTaskQueueItemState,
    priority: AiTaskQueueItemPriority = AiTaskQueueItemPriority.NORMAL,
    progressStage: AiTaskQueueProgressStage? = null,
    progressCurrent: Int? = null,
    progressTotal: Int? = null,
  ) = AiTaskQueueItem(
    id = id,
    kind = AiTaskQueueItemKind.SUMMARY,
    title = id,
    source = "test",
    state = state,
    priority = priority,
    progressStage = progressStage,
    progressCurrent = progressCurrent,
    progressTotal = progressTotal,
  )
}
