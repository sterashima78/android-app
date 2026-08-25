package dev.terashima.yomitorirss.feature.aitaskqueue.data

import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskSnapshot
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskState
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionProvider
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeTaskQueueAdapterTest {
  @Test
  fun `Knowledge task exposes selected ChatGPT provider`() = runBlocking {
    val settings = FakeKnowledgeExecutionSettings(KnowledgeExecutionProvider.CHATGPT)
    val adapter = KnowledgeTaskQueueAdapter(FakeKnowledgeController(), settings)

    val task = adapter.tasks().single()

    assertEquals("ChatGPT", task.executionProviderLabel)
    assertTrue(adapter.usesCloudProvider())
    assertFalse(adapter.usesLocalProvider())
  }

  @Test
  fun `Knowledge task exposes selected Local provider`() = runBlocking {
    val settings = FakeKnowledgeExecutionSettings(KnowledgeExecutionProvider.LOCAL)
    val adapter = KnowledgeTaskQueueAdapter(FakeKnowledgeController(), settings)

    assertEquals("Local", adapter.tasks().single().executionProviderLabel)
    assertTrue(adapter.usesLocalProvider())
    assertFalse(adapter.usesCloudProvider())
  }
}

private class FakeKnowledgeExecutionSettings(initial: KnowledgeExecutionProvider) : KnowledgeExecutionSettings {
  private val mutableProvider = MutableStateFlow(initial)
  override val provider: StateFlow<KnowledgeExecutionProvider> = mutableProvider
  override fun currentProvider(): KnowledgeExecutionProvider = mutableProvider.value
  override fun setProvider(provider: KnowledgeExecutionProvider) {
    mutableProvider.value = provider
  }
}

private class FakeKnowledgeController : KnowledgeBuildTaskController {
  override fun kick() = Unit
  override suspend fun pauseForGlobalGate() = Unit
  override suspend fun stop(): Boolean = false
  override suspend fun cancel(): Boolean = false
  override suspend fun resume(): Boolean = false
  override suspend fun snapshot(): KnowledgeBuildTaskSnapshot =
    KnowledgeBuildTaskSnapshot(KnowledgeBuildTaskState.QUEUED)
  override fun setResumeOnChargingScheduled(enabled: Boolean) = Unit
}
