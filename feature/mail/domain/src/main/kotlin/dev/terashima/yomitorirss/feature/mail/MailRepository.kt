package dev.terashima.yomitorirss.feature.mail

sealed interface MailInitialSyncStep {
  data class Continue(val nextPageToken: String) : MailInitialSyncStep
  data object Complete : MailInitialSyncStep
  data object Stale : MailInitialSyncStep
}

interface MailRepository {
  suspend fun getAccounts(): List<MailAccount>

  suspend fun connectAccount(
    email: String,
    displayName: String?,
    accessToken: String,
  ): MailAccount

  suspend fun removeAccount(accountId: String)

  suspend fun getThreads(
    accountId: String? = null,
    mailbox: Mailbox = Mailbox.INBOX,
    query: String = "",
  ): List<MailThread>

  suspend fun getThread(accountId: String, threadId: String): MailThread

  suspend fun getLabels(accountId: String): List<MailLabel>

  suspend fun sync(accountId: String? = null)

  suspend fun syncInitialPage(
    accountId: String,
    expectedPageToken: String?,
  ): MailInitialSyncStep

  fun markInitialSyncWaitingForNetwork(accountId: String, message: String?)

  fun markInitialSyncError(accountId: String, message: String?)

  fun refreshPeriodicSyncPolicy()

  suspend fun setThreadRead(accountId: String, threadId: String, read: Boolean)

  suspend fun setThreadStarred(accountId: String, threadId: String, starred: Boolean)

  suspend fun setThreadReadLater(accountId: String, threadId: String, readLater: Boolean)

  suspend fun archiveThread(accountId: String, threadId: String)

  suspend fun trashThread(accountId: String, threadId: String)

  suspend fun applyLabel(accountId: String, threadId: String, labelId: String)
}
