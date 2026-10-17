package dev.terashima.yomitorirss.feature.rss

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RssReadLaterPriorityFeedbackTest {
  @Test
  fun `あとで読む保存が成功した場合だけ優先参考に追加して学習を予約する`() = runBlocking {
    val calls = mutableListOf<String>()

    markReadLaterWithPriorityFeedback(
      saveReadLater = { calls += "saved"; true },
      recordPriority = { calls += "priority" },
      scheduleLearning = { calls += "scheduled" },
      onPriorityError = { calls += "error" },
    )

    assertEquals(listOf("saved", "priority", "scheduled"), calls)
  }

  @Test
  fun `既にあとで読む場合は優先参考と学習予約を重複させない`() = runBlocking {
    val calls = mutableListOf<String>()
    markReadLaterWithPriorityFeedback(
      saveReadLater = { calls += "saved"; false },
      recordPriority = { calls += "priority" },
      scheduleLearning = { calls += "scheduled" },
      onPriorityError = { calls += "error" },
    )
    assertEquals(listOf("saved"), calls)
  }

  @Test
  fun `あとで読む保存失敗時に優先参考を追加しない`() = runBlocking {
    val calls = mutableListOf<String>()
    val failure = IllegalStateException("cannot save")

    val thrown = runCatching {
      markReadLaterWithPriorityFeedback(
        saveReadLater = { calls += "save"; throw failure },
        recordPriority = { calls += "priority" },
        scheduleLearning = { calls += "scheduled" },
        onPriorityError = { calls += "error" },
      )
    }.exceptionOrNull()

    assertSame(failure, thrown)
    assertEquals(listOf("save"), calls)
  }

  @Test
  fun `優先参考登録失敗時でもあとで読むは保存成功として扱う`() = runBlocking {
    val calls = mutableListOf<String>()
    val failure = IllegalStateException("cannot record")
    var reported: Throwable? = null

    markReadLaterWithPriorityFeedback(
      saveReadLater = { calls += "saved"; true },
      recordPriority = { calls += "priority"; throw failure },
      scheduleLearning = { calls += "scheduled" },
      onPriorityError = { reported = it },
    )

    assertEquals(listOf("saved", "priority"), calls)
    assertSame(failure, reported)
  }

  @Test
  fun `推薦サービスがない場合もあとで読むだけを保存する`() = runBlocking {
    val calls = mutableListOf<String>()

    markReadLaterWithPriorityFeedback(
      saveReadLater = { calls += "saved"; true },
      recordPriority = null,
      scheduleLearning = { calls += "scheduled" },
      onPriorityError = { calls += "error" },
    )

    assertEquals(listOf("saved"), calls)
  }

  @Test
  fun `学習予約が失敗しても保存と優先参考登録は維持する`() = runBlocking {
    val calls = mutableListOf<String>()

    markReadLaterWithPriorityFeedback(
      saveReadLater = { calls += "saved"; true },
      recordPriority = { calls += "priority" },
      scheduleLearning = { calls += "schedule"; error("failed to schedule") },
      onPriorityError = { calls += "error" },
    )

    assertEquals(listOf("saved", "priority", "schedule"), calls)
  }
}
