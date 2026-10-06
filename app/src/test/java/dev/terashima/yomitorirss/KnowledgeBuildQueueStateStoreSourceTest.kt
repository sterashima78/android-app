package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeBuildQueueStateStoreSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "feature").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `Knowledge build storeは新attemptで停止失敗と旧topicを引き継がない`() {
    val source = storeSource()
    val markReady = source
      .substringAfter("fun markReady(): String = synchronized(LOCK) {")
      .substringBefore("fun markRetrying")
    val startNewAttempt = source
      .substringAfter("private fun startNewAttempt(): String {")
      .substringBefore("private fun clearLocked")

    assertTrue("resume must start a fresh attempt", "startNewAttempt()" in markReady)
    assertTrue("fresh attempt must set requested", "putBoolean(KEY_REQUESTED, true)" in startNewAttempt)
    assertTrue("fresh attempt must clear stopped", "putBoolean(KEY_STOPPED, false)" in startNewAttempt)
    assertTrue("fresh attempt must clear failed", "putBoolean(KEY_FAILED, false)" in startNewAttempt)
    assertTrue("fresh attempt must clear pending topics", "remove(KEY_PENDING_TOPIC_IDS)" in startNewAttempt)
    assertTrue("fresh attempt must clear prior error", "remove(KEY_ERROR)" in startNewAttempt)
  }

  @Test
  fun `retryable failureはterminal failureへ確定しない`() {
    val source = storeSource()
    val retrying = source
      .substringAfter("fun markRetrying(requestId: String, message: String)")
      .substringBefore("fun markFailed")

    assertTrue("retry must keep the current request", "KEY_REQUEST_ID" in retrying)
    assertTrue("retry must record the retry message", "putString(KEY_ERROR" in retrying)
    assertFalse("retry must not mark the attempt failed", "putBoolean(KEY_FAILED, true)" in retrying)
  }

  @Test
  fun `terminal failureと最後のtopic完了はdurable stateへ反映する`() {
    val source = storeSource()
    val failed = source
      .substringAfter("fun markFailed(requestId: String, message: String)")
      .substringBefore("fun complete")
    val topicCompleted = source
      .substringAfter("fun markTopicCompleted(requestId: String, topicId: String)")
      .substringBefore("fun markStopped")

    assertTrue("terminal failure must set the failed flag", "putBoolean(KEY_FAILED, true)" in failed)
    assertTrue(
      "last topic must complete only for an active non-failed non-stopped attempt",
      "pending.isEmpty() && !failed && !stopped" in topicCompleted,
    )
    assertTrue("last topic completion must clear the durable request", "clearLocked()" in topicCompleted)
  }

  private fun storeSource(): String = File(
    repositoryRoot,
    "feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeBuildQueueStateStore.kt",
  ).readText()
}
