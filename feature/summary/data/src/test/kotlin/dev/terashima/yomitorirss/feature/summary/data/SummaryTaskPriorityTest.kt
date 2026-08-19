package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTaskPriorityTest {
  @Test
  fun `あとで読むタスクは先に追加された通常タスクより優先する`() {
    val normal = task("normal", "2026-08-16T00:00:00Z")
    val readLater = task("read-later", "2026-08-16T00:00:01Z")

    val selected = selectNextSummaryTask(listOf(normal, readLater), setOf("read-later"))

    assertEquals("read-later", selected?.articleId)
    assertEquals(LocalAiBackgroundTaskPriority.HIGH, summaryTaskPriority(checkNotNull(selected), setOf("read-later")))
  }

  @Test
  fun `高優先度対象から外れると通常優先度になる`() {
    val candidate = task("article", "2026-08-16T00:00:00Z")

    assertEquals(LocalAiBackgroundTaskPriority.HIGH, summaryTaskPriority(candidate, setOf("article")))
    assertEquals(LocalAiBackgroundTaskPriority.NORMAL, summaryTaskPriority(candidate, emptySet()))
  }

  private fun task(id: String, queuedAt: String) = SummaryTaskRecord(
    articleId = id,
    state = SUMMARY_QUEUED,
    forceRefresh = false,
    queuedAt = queuedAt,
    startedAt = null,
    finishedAt = null,
    error = null,
    progressStage = null,
    progressCurrent = null,
    progressTotal = null,
  )
}
