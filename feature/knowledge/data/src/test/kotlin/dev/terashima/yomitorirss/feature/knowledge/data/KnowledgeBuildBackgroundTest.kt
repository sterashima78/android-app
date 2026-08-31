package dev.terashima.yomitorirss.feature.knowledge.data

import androidx.work.ExistingWorkPolicy
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskState
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionProvider
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
  fun `計画済みトピックがあればキューは実行中として投影する`() {
    assertEquals(KnowledgeBuildTaskState.RUNNING, knowledgeBuildTaskState(hasPendingTopics = true))
    assertEquals(KnowledgeBuildTaskState.QUEUED, knowledgeBuildTaskState(hasPendingTopics = false))
  }

  @Test
  fun `LocalトピックWorkだけを直列化する`() {
    assertTrue(shouldSerializeKnowledgeTopicWork(KnowledgeExecutionProvider.LOCAL))
    assertFalse(shouldSerializeKnowledgeTopicWork(KnowledgeExecutionProvider.CHATGPT))
  }
}
