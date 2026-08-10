package dev.terashima.yomitorirss.feature.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class MailViewModelTest {
  @Test
  fun `未読は受信トレイ内のスレッドだけを表示する`() {
    val inboxUnread = thread(id = "inbox-unread", isInInbox = true, isUnread = true)
    val archivedUnread = thread(id = "archived-unread", isInInbox = false, isUnread = true)

    assertEquals(
      listOf(inboxUnread),
      listOf(inboxUnread, archivedUnread).forMailbox(Mailbox.UNREAD),
    )
  }

  @Test
  fun `アーカイブは受信トレイ外のスレッドだけを表示する`() {
    val inbox = thread(id = "inbox", isInInbox = true)
    val archived = thread(id = "archived", isInInbox = false)

    assertEquals(
      listOf(archived),
      listOf(inbox, archived).forMailbox(Mailbox.ALL_MAIL),
    )
  }

  @Test
  fun `スターは受信トレイ状態に関係なくリポジトリ結果を維持する`() {
    val starredInbox = thread(id = "starred-inbox", isInInbox = true, isStarred = true)
    val starredArchived = thread(id = "starred-archived", isInInbox = false, isStarred = true)
    val threads = listOf(starredInbox, starredArchived)

    assertEquals(threads, threads.forMailbox(Mailbox.STARRED))
  }

  private fun thread(
    id: String,
    isInInbox: Boolean,
    isUnread: Boolean = false,
    isStarred: Boolean = false,
  ) = MailThread(
    id = id,
    accountId = "account@example.com",
    subject = id,
    snippet = "",
    lastMessageAtEpochMillis = 0L,
    messageCount = 1,
    isInInbox = isInInbox,
    isUnread = isUnread,
    isStarred = isStarred,
  )
}
