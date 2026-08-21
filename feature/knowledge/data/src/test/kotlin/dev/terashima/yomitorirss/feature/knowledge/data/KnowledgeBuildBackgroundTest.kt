package dev.terashima.yomitorirss.feature.knowledge.data

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
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
}
