package dev.terashima.yomitorirss.feature.mail

enum class MailSyncState {
  IDLE,
  SYNCING,
  WAITING_FOR_NETWORK,
  ERROR,
}

data class MailAccount(
  val id: String,
  val email: String,
  val displayName: String? = null,
  val lastSyncedAtEpochMillis: Long? = null,
  val syncState: MailSyncState = MailSyncState.IDLE,
  val syncProcessedThreads: Int = 0,
  val syncError: String? = null,
)

data class MailLabel(
  val id: String,
  val accountId: String,
  val name: String,
  val type: String,
)

data class MailMessage(
  val id: String,
  val threadId: String,
  val accountId: String,
  val sender: String,
  val recipients: String,
  val subject: String,
  val snippet: String,
  val body: String,
  val htmlBody: String? = null,
  val receivedAtEpochMillis: Long,
  val labelIds: Set<String>,
  val isUnread: Boolean,
  val isStarred: Boolean,
)

data class MailThread(
  val id: String,
  val accountId: String,
  val subject: String,
  val snippet: String,
  val lastMessageAtEpochMillis: Long,
  val messageCount: Int,
  val isInInbox: Boolean,
  val isUnread: Boolean,
  val isStarred: Boolean,
  val isReadLater: Boolean = false,
  val messages: List<MailMessage> = emptyList(),
)

enum class Mailbox(val label: String) {
  INBOX("受信"),
  UNREAD("未読"),
  READ_LATER("あとで読む"),
  STARRED("スター"),
  ALL_MAIL("アーカイブ"),
}

class MailAuthorizationRequiredException(
  val accountEmail: String,
) : IllegalStateException("Google アカウントの再認証が必要です: $accountEmail")
