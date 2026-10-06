package dev.terashima.yomitorirss

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.knowledge.data.KnowledgeBuildQueueStateStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KnowledgeBuildQueueStateStoreTest {
  private lateinit var context: Context
  private lateinit var store: KnowledgeBuildQueueStateStore

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    store = KnowledgeBuildQueueStateStore(context)
    store.clear()
  }

  @After
  fun tearDown() {
    store.clear()
  }

  @Test
  fun `停止したbuildを再開すると新attemptになりpending topicを持ち越さない`() {
    val firstRequestId = store.request()
    assertTrue(store.setPlannedTopics(firstRequestId, listOf("topic-1")))
    store.markStopped()
    assertTrue(store.stopped)
    assertTrue(store.hasPendingTopics)

    val resumedRequestId = store.markReady()

    assertNotEquals(firstRequestId, resumedRequestId)
    assertTrue(store.requested)
    assertFalse(store.stopped)
    assertFalse(store.failed)
    assertFalse(store.hasPendingTopics)
    assertNull(store.error)
  }

  @Test
  fun `失敗したbuildを再開するとerrorとpending topicを持ち越さない`() {
    val firstRequestId = store.request()
    assertTrue(store.setPlannedTopics(firstRequestId, listOf("topic-1")))
    store.markFailed(firstRequestId, "temporary failure")
    assertTrue(store.failed)
    assertTrue(store.hasPendingTopics)

    val resumedRequestId = store.markReady()

    assertNotEquals(firstRequestId, resumedRequestId)
    assertTrue(store.requested)
    assertFalse(store.stopped)
    assertFalse(store.failed)
    assertFalse(store.hasPendingTopics)
    assertNull(store.error)
  }

  @Test
  fun `最後のplanned topic完了でbuild要求を消去する`() {
    val requestId = store.request()
    assertTrue(store.setPlannedTopics(requestId, listOf("topic-1")))

    assertTrue(store.markTopicCompleted(requestId, "topic-1"))

    assertFalse(store.requested)
    assertFalse(store.hasPendingTopics)
    assertFalse(store.stopped)
    assertFalse(store.failed)
    assertNull(store.error)
  }

  @Test
  fun `retryable failureは同じattemptをFAILEDへ確定しない`() {
    val requestId = store.request()
    assertTrue(store.setPlannedTopics(requestId, listOf("topic-1")))

    store.markRetrying(requestId, "retry later")

    assertTrue(store.requested)
    assertFalse(store.stopped)
    assertFalse(store.failed)
    assertTrue(store.hasPendingTopics)
    assertTrue(store.error?.contains("retry later") == true)
  }
}
