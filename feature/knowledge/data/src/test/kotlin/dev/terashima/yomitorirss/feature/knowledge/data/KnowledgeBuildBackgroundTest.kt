package dev.terashima.yomitorirss.feature.knowledge.data

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeBuildBackgroundTest {
  @Test
  fun `明示的な再生成要求は既存Workを置き換える`() {
    assertEquals(
      ExistingWorkPolicy.REPLACE,
      knowledgeBuildExistingWorkPolicy(forceReschedule = true),
    )
  }

  @Test
  fun `通常の復帰処理は既存Workを維持する`() {
    assertEquals(
      ExistingWorkPolicy.KEEP,
      knowledgeBuildExistingWorkPolicy(forceReschedule = false),
    )
  }

  @Test
  fun `計画済みgenerationへの通常kickは再計画しない`() {
    assertTrue(
      shouldSkipKnowledgeBuildKick(
        forceReschedule = false,
        hasPendingTopics = true,
      ),
    )
    assertFalse(
      shouldSkipKnowledgeBuildKick(
        forceReschedule = true,
        hasPendingTopics = true,
      ),
    )
    assertFalse(
      shouldSkipKnowledgeBuildKick(
        forceReschedule = false,
        hasPendingTopics = false,
      ),
    )
  }

  @Test
  fun `計画済みトピックがあればキューは実行中として投影する`() {
    assertEquals(KnowledgeBuildTaskState.RUNNING, knowledgeBuildTaskState(hasPendingTopics = true))
    assertEquals(KnowledgeBuildTaskState.QUEUED, knowledgeBuildTaskState(hasPendingTopics = false))
  }

  @Test
  fun `期限切れでも実行中のKnowledge AI入力は削除しない`() {
    assertFalse(
      shouldDeleteKnowledgePageAiRequest(
        expired = true,
        state = WorkInfo.State.RUNNING,
      ),
    )
    assertFalse(
      shouldDeleteKnowledgePageAiRequest(
        expired = true,
        state = WorkInfo.State.ENQUEUED,
      ),
    )
  }

  @Test
  fun `期限切れで終了済みまたは対応WorkなしのKnowledge AI入力は削除できる`() {
    assertTrue(
      shouldDeleteKnowledgePageAiRequest(
        expired = true,
        state = WorkInfo.State.SUCCEEDED,
      ),
    )
    assertTrue(
      shouldDeleteKnowledgePageAiRequest(
        expired = true,
        state = null,
      ),
    )
    assertFalse(
      shouldDeleteKnowledgePageAiRequest(
        expired = false,
        state = WorkInfo.State.SUCCEEDED,
      ),
    )
  }
}
