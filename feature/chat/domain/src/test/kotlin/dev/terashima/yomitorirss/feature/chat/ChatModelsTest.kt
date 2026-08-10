package dev.terashima.yomitorirss.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatModelsTest {
  @Test
  fun `進捗のモデル名と見積時間は省略できる`() {
    val progress = ChatProgress(stage = "loading")

    assertEquals("loading", progress.stage)
    assertNull(progress.modelName)
    assertNull(progress.estimatedStageDurationMillis)
  }
}
