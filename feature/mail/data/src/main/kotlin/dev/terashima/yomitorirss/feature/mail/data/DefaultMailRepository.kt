package dev.terashima.yomitorirss.feature.mail.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.mail.MailAccount
import dev.terashima.yomitorirss.feature.mail.MailLabel
import dev.terashima.yomitorirss.feature.mail.MailMessage
import dev.terashima.yomitorirss.feature.mail.MailRepository
import dev.terashima.yomitorirss.feature.mail.MailSyncState
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.Mailbox
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal sealed interface InitialSyncStep {
  data class Continue(val nextPageToken: String) : InitialSyncStep
  data object Complete : InitialSyncStep
  data object Stale : InitialSyncStep
}

class DefaultMailRepository(
  context: Context,
  private val database: DatabaseConnection,
  private val authorization: GmailAuthorizationManager,
) : MailRepository {
  private val api = GmailApiClient()
  private val syncScheduler = MailSyncScheduler(context.applicationContext)

  override suspend fun getAccounts(): List<MailAccount> = database.readable.query(
    "mail_accounts",
    ACCOUNT_COLUMNS,
    null,
    null,
    null,
    null,
    "email COLLATE NOCASE",
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.mailAccount()) } }

  override suspend fun connectAccount(
    email: String,
    displayName: String?,
    accessToken: String,
  ): MailAccount {
    val normalizedEmail = email.trim().lowercase()
    require(normalizedEmail.isNotBlank()) { "Google アカウントを取得できませんでした" }
    authorization.remember(normalizedEmail, accessToken)
    val account = MailAccount(
      id = normalizedEmail,
      email = normalizedEmail,
      displayName = displayName?.trim()?.takeIf(String::isNotBlank),
    )
    upsertAccount(account)
    prepareFreshInitialSync(account.id)
    syncScheduler.scheduleInitialPage(account.id, expectedPageToken = null)
    syncScheduler.schedulePeriodic()
    return getAccounts().first { it.id == account.id }
  }

  override suspend fun removeAccount(accountId: String) {
    syncScheduler.cancelAccount(accountId)
    database.writable.delete("mail_accounts", "id = ?", arrayOf(accountId))
    if (getAccounts().isEmpty()) syncScheduler.cancelPeriodic()
  }

  override suspend fun getThreads(
    accountId: String?,
    mailbox: Mailbox,
    query: String,
  ): List<MailThread> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isNotEmpty() && mailbox != Mailbox.READ_LATER) {
      return remoteSearch(accountId, mailbox, trimmedQuery)
    }
    val selection = buildList {
      if (accountId != null) add("account_id = ?")
      when (mailbox) {
        Mailbox.INBOX -> add("in_inbox = 1")
        Mailbox.UNREAD -> {
          add("is_unread = 1")
          add("in_inbox = 1")
        }
        Mailbox.READ_LATER -> add("read_later_locally = 1")
        Mailbox.STARRED -> {
          add("is_starred = 1")
          add("(in_inbox = 1 OR archived_locally = 1 OR read_later_locally = 1)")
        }
        Mailbox.ALL_MAIL -> {
          add("in_inbox = 0")
          add("archived_locally = 1")
        }
      }
    }.joinToString(" AND ").ifBlank { null }
    val args = accountId?.let { arrayOf(it) }
    val threads = database.readable.query(
      "mail_threads",
      THREAD_COLUMNS,
      selection,
      args,
      null,
      null,
      "last_message_at DESC",
      "100",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.mailThread()) } }
    if (mailbox != Mailbox.READ_LATER || trimmedQuery.isEmpty()) return threads
    return threads.filter { thread ->
      thread.subject.contains(trimmedQuery, ignoreCase = true) ||
        thread.snippet.contains(trimmedQuery, ignoreCase = true)
    }
  }

  override suspend fun getThread(accountId: String, threadId: String): MailThread {
    val thread = database.readable.query(
      "mail_threads",
      THREAD_COLUMNS,
      "account_id = ? AND id = ?",
      arrayOf(accountId, threadId),
      null,
      null,
      null,
      "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.mailThread() else null }
      ?: error("メールスレッドが見つかりません")
    val messages = database.readable.query(
      "mail_messages",
      MESSAGE_COLUMNS,
      "account_id = ? AND thread_id = ?",
      arrayOf(accountId, threadId),
      null,
      null,
      "received_at ASC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.mailMessage()) } }
    return thread.copy(messages = messages)
  }

  override suspend fun getLabels(accountId: String): List<MailLabel> = database.readable.query(
    "mail_labels",
    LABEL_COLUMNS,
    "account_id = ?",
    arrayOf(accountId),
    null,
    null,
    "CASE type WHEN 'system' THEN 0 ELSE 1 END, name COLLATE NOCASE",
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.mailLabel()) } }

  override suspend fun sync(accountId: String?) {
    val accounts = getAccounts().filter { accountId == null || it.id == accountId }
    for (account in accounts) {
      val historyId = historyId(account.id)
      if (historyId == null) {
        resumeInitialSync(account.id)
        continue
      }

      val token = authorization.accessToken(account.email)
      try {
        partialSync(account, token, historyId)
      } catch (_: GmailHistoryExpiredException) {
        prepareFreshInitialSync(account.id)
        syncScheduler.scheduleInitialPage(account.id, expectedPageToken = null)
      }
    }
  }

  internal suspend fun syncInitialPage(
    accountId: String,
    expectedPageToken: String?,
  ): InitialSyncStep {
    val account = account(accountId)
    val state = initialSyncState(accountId)
    if (state.pageToken != expectedPageToken) return InitialSyncStep.Stale

    markInitialSyncRunning(accountId)
    val accessToken = authorization.accessToken(account.email)
    val startHistoryId = state.startHistoryId ?: api.profileHistoryId(accessToken).also { historyId ->
      updateInitialSyncCheckpoint(accountId, startHistoryId = historyId)
    }
    val generation = state.generation ?: UUID.randomUUID().toString().also { value ->
      updateInitialSyncCheckpoint(accountId, generation = value)
    }

    val page = api.listThreadPage(
      accessToken = accessToken,
      query = INITIAL_SYNC_QUERY,
      maxResults = INITIAL_SYNC_PAGE_SIZE,
      pageToken = state.pageToken,
    )
    for (batch in page.threadIds.chunked(THREAD_FETCH_CONCURRENCY)) {
      val threads = coroutineScope {
        batch.map { threadId ->
          async {
            try {
              api.getThread(accessToken, account.id, threadId)
            } catch (error: GmailApiException) {
              if (error.statusCode == 404) null else throw error
            }
          }
        }.awaitAll()
      }
      threads.filterNotNull().forEach { thread -> upsertThread(thread, syncGeneration = generation) }
      incrementInitialSyncProgress(accountId, batch.size)
    }

    val nextPageToken = page.nextPageToken
    if (nextPageToken != null) {
      updateInitialSyncCheckpoint(accountId, pageToken = nextPageToken, updatePageToken = true)
      return InitialSyncStep.Continue(nextPageToken)
    }

    replaceLabels(account.id, api.listLabels(accessToken, account.id))
    removeStaleThreadsFromGeneration(account.id, generation)
    completeInitialSync(
      accountId = account.id,
      historyId = startHistoryId,
      syncedAt = System.currentTimeMillis(),
    )
    return InitialSyncStep.Complete
  }

  internal fun markInitialSyncWaitingForNetwork(accountId: String, message: String?) {
    updateInitialSyncStatus(accountId, SYNC_STATE_WAITING_FOR_NETWORK, message)
  }

  internal fun markInitialSyncError(accountId: String, message: String?) {
    updateInitialSyncStatus(accountId, SYNC_STATE_ERROR, message)
  }

  override suspend fun setThreadRead(accountId: String, threadId: String, read: Boolean) {
    modify(
      accountId = accountId,
      threadId = threadId,
      add = if (read) emptyList() else listOf("UNREAD"),
      remove = if (read) listOf("UNREAD") else emptyList(),
    )
  }

  override suspend fun setThreadStarred(accountId: String, threadId: String, starred: Boolean) {
    modify(
      accountId = accountId,
      threadId = threadId,
      add = if (starred) listOf("STARRED") else emptyList(),
      remove = if (starred) emptyList() else listOf("STARRED"),
    )
  }

  override suspend fun setThreadReadLater(accountId: String, threadId: String, readLater: Boolean) {
    database.writable.update(
      "mail_threads",
      ContentValues().apply { put("read_later_locally", readLater.asInt()) },
      "account_id = ? AND id = ?",
      arrayOf(accountId, threadId),
    )
  }

  override suspend fun archiveThread(accountId: String, threadId: String) {
    setArchiveState(accountId, threadId, archived = true)
  }

  override suspend fun trashThread(accountId: String, threadId: String) {
    val account = account(accountId)
    val token = authorization.accessToken(account.email)
    api.trashThread(token, threadId)
    deleteThread(accountId, threadId)
  }

  override suspend fun applyLabel(accountId: String, threadId: String, labelId: String) {
    if (labelId == "INBOX") {
      setArchiveState(accountId, threadId, archived = false)
    } else {
      modify(accountId, threadId, add = listOf(labelId))
    }
  }

  private suspend fun modify(
    accountId: String,
    threadId: String,
    add: Collection<String> = emptyList(),
    remove: Collection<String> = emptyList(),
  ) {
    val account = account(accountId)
    val token = authorization.accessToken(account.email)
    api.modifyThread(token, threadId, add, remove)
    upsertThread(api.getThread(token, accountId, threadId))
  }

  private suspend fun setArchiveState(
    accountId: String,
    threadId: String,
    archived: Boolean,
  ) {
    val account = account(accountId)
    val token = authorization.accessToken(account.email)
    api.modifyThread(
      accessToken = token,
      threadId = threadId,
      addLabelIds = if (archived) emptyList() else listOf("INBOX"),
      removeLabelIds = if (archived) listOf("INBOX") else emptyList(),
    )
    database.writable.update(
      "mail_threads",
      ContentValues().apply {
        put("in_inbox", (!archived).asInt())
        put("archived_locally", archived.asInt())
      },
      "account_id = ? AND id = ?",
      arrayOf(accountId, threadId),
    )
  }

  private suspend fun remoteSearch(
    accountId: String?,
    mailbox: Mailbox,
    query: String,
  ): List<MailThread> {
    val accounts = getAccounts().filter { accountId == null || it.id == accountId }
    return buildList {
      for (account in accounts) {
        val token = authorization.accessToken(account.email)
        val gmailQuery = listOf(mailbox.gmailQuery, query).filter(String::isNotBlank).joinToString(" ")
        for (threadId in api.listThreadIds(token, gmailQuery, maxResults = 50)) {
          val thread = api.getThread(token, account.id, threadId)
          if (!thread.isInInbox && !isRetainedLocally(thread.accountId, thread.id)) continue
          upsertThread(thread)
          add(getThread(thread.accountId, thread.id).copy(messages = emptyList()))
        }
      }
    }.distinctBy { "${it.accountId}:${it.id}" }.sortedByDescending(MailThread::lastMessageAtEpochMillis)
  }

  private suspend fun partialSync(
    account: MailAccount,
    accessToken: String,
    startHistoryId: String,
  ) {
    val delta = api.history(accessToken, startHistoryId)
    for (threadId in delta.threadIds) {
      try {
        val thread = api.getThread(accessToken, account.id, threadId)
        if (thread.isInInbox || isRetainedLocally(account.id, threadId)) {
          upsertThread(thread)
        } else {
          deleteThread(account.id, threadId)
        }
      } catch (error: GmailApiException) {
        if (error.statusCode == 404) deleteThread(account.id, threadId) else throw error
      }
    }
    replaceLabels(account.id, api.listLabels(accessToken, account.id))
    updateSyncState(account.id, delta.historyId, System.currentTimeMillis())
  }

  private fun resumeInitialSync(accountId: String) {
    val state = initialSyncState(accountId)
    if (state.generation == null) prepareFreshInitialSync(accountId) else markInitialSyncRunning(accountId)
    syncScheduler.scheduleInitialPage(accountId, initialSyncState(accountId).pageToken)
  }

  private fun prepareFreshInitialSync(accountId: String) {
    database.writable.update(
      "mail_accounts",
      ContentValues().apply {
        putNull("last_history_id")
        put(SYNC_STATE_COLUMN, SYNC_STATE_SYNCING)
        put(SYNC_PROCESSED_COLUMN, 0)
        putNull(SYNC_ERROR_COLUMN)
        putNull(SYNC_PAGE_TOKEN_COLUMN)
        putNull(SYNC_START_HISTORY_COLUMN)
        put(SYNC_GENERATION_COLUMN, UUID.randomUUID().toString())
      },
      "id = ?",
      arrayOf(accountId),
    )
  }

  private fun markInitialSyncRunning(accountId: String) {
    updateInitialSyncStatus(accountId, SYNC_STATE_SYNCING, error = null)
  }

  private fun updateInitialSyncStatus(accountId: String, state: String, error: String?) {
    database.writable.update(
      "mail_accounts",
      ContentValues().apply {
        put(SYNC_STATE_COLUMN, state)
        if (error == null) putNull(SYNC_ERROR_COLUMN) else put(SYNC_ERROR_COLUMN, error.take(500))
      },
      "id = ?",
      arrayOf(accountId),
    )
  }

  private fun updateInitialSyncCheckpoint(
    accountId: String,
    startHistoryId: String? = null,
    generation: String? = null,
    pageToken: String? = null,
    updatePageToken: Boolean = false,
  ) {
    database.writable.update(
      "mail_accounts",
      ContentValues().apply {
        startHistoryId?.let { put(SYNC_START_HISTORY_COLUMN, it) }
        generation?.let { put(SYNC_GENERATION_COLUMN, it) }
        if (updatePageToken) {
          if (pageToken == null) putNull(SYNC_PAGE_TOKEN_COLUMN) else put(SYNC_PAGE_TOKEN_COLUMN, pageToken)
        }
      },
      "id = ?",
      arrayOf(accountId),
    )
  }

  private fun incrementInitialSyncProgress(accountId: String, count: Int) {
    if (count <= 0) return
    database.writable.execSQL(
      "UPDATE mail_accounts SET $SYNC_PROCESSED_COLUMN = $SYNC_PROCESSED_COLUMN + ? WHERE id = ?",
      arrayOf<Any>(count, accountId),
    )
  }

  private fun completeInitialSync(accountId: String, historyId: String, syncedAt: Long) {
    database.writable.update(
      "mail_accounts",
      ContentValues().apply {
        put("last_history_id", historyId)
        put("last_synced_at", syncedAt)
        put(SYNC_STATE_COLUMN, SYNC_STATE_IDLE)
        putNull(SYNC_ERROR_COLUMN)
        putNull(SYNC_PAGE_TOKEN_COLUMN)
        putNull(SYNC_START_HISTORY_COLUMN)
        putNull(SYNC_GENERATION_COLUMN)
      },
      "id = ?",
      arrayOf(accountId),
    )
  }

  private data class InitialSyncState(
    val pageToken: String?,
    val startHistoryId: String?,
    val generation: String?,
  )

  private fun initialSyncState(accountId: String): InitialSyncState = database.readable.query(
    "mail_accounts",
    arrayOf(SYNC_PAGE_TOKEN_COLUMN, SYNC_START_HISTORY_COLUMN, SYNC_GENERATION_COLUMN),
    "id = ?",
    arrayOf(accountId),
    null,
    null,
    null,
    "1",
  ).use { cursor ->
    if (!cursor.moveToFirst()) error("メールアカウントが見つかりません")
    InitialSyncState(
      pageToken = cursor.stringOrNull(0),
      startHistoryId = cursor.stringOrNull(1),
      generation = cursor.stringOrNull(2),
    )
  }

  private fun account(accountId: String): MailAccount = database.readable.query(
    "mail_accounts",
    ACCOUNT_COLUMNS,
    "id = ?",
    arrayOf(accountId),
    null,
    null,
    null,
    "1",
  ).use { cursor -> if (cursor.moveToFirst()) cursor.mailAccount() else error("メールアカウントが見つかりません") }

  private fun historyId(accountId: String): String? = database.readable.rawQuery(
    "SELECT last_history_id FROM mail_accounts WHERE id = ?",
    arrayOf(accountId),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.stringOrNull(0) else null }

  private fun upsertAccount(account: MailAccount) {
    database.writable.insertWithOnConflict(
      "mail_accounts",
      null,
      ContentValues().apply {
        put("id", account.id)
        put("email", account.email)
        put("display_name", account.displayName)
      },
      SQLiteDatabase.CONFLICT_IGNORE,
    )
    database.writable.update(
      "mail_accounts",
      ContentValues().apply {
        put("email", account.email)
        put("display_name", account.displayName)
      },
      "id = ?",
      arrayOf(account.id),
    )
  }

  private fun updateSyncState(accountId: String, historyId: String, syncedAt: Long) {
    database.writable.update(
      "mail_accounts",
      ContentValues().apply {
        put("last_history_id", historyId)
        put("last_synced_at", syncedAt)
        put(SYNC_STATE_COLUMN, SYNC_STATE_IDLE)
        putNull(SYNC_ERROR_COLUMN)
      },
      "id = ?",
      arrayOf(accountId),
    )
  }

  private fun replaceLabels(accountId: String, labels: List<MailLabel>) {
    database.transaction {
      delete("mail_labels", "account_id = ?", arrayOf(accountId))
      labels.forEach { label ->
        insertWithOnConflict(
          "mail_labels",
          null,
          ContentValues().apply {
            put("account_id", label.accountId)
            put("id", label.id)
            put("name", label.name)
            put("type", label.type)
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
      }
    }
  }

  private fun upsertThread(thread: MailThread, syncGeneration: String? = null) {
    val effectiveGeneration = syncGeneration
      ?: activeSyncGeneration(thread.accountId)
      ?: threadGeneration(thread.accountId, thread.id)
    val effectiveArchivedLocally = if (thread.isInInbox) false else isArchivedLocally(thread.accountId, thread.id)
    val effectiveReadLater = isReadLaterLocally(thread.accountId, thread.id)
    database.transaction {
      insertWithOnConflict(
        "mail_threads",
        null,
        ContentValues().apply {
          put("account_id", thread.accountId)
          put("id", thread.id)
          put("subject", thread.subject)
          put("snippet", thread.snippet)
          put("last_message_at", thread.lastMessageAtEpochMillis)
          put("message_count", thread.messageCount)
          put("in_inbox", thread.isInInbox.asInt())
          put("is_unread", thread.isUnread.asInt())
          put("is_starred", thread.isStarred.asInt())
          put("archived_locally", effectiveArchivedLocally.asInt())
          put("read_later_locally", effectiveReadLater.asInt())
          if (effectiveGeneration == null) putNull(SYNC_GENERATION_COLUMN) else put(SYNC_GENERATION_COLUMN, effectiveGeneration)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      delete(
        "mail_messages",
        "account_id = ? AND thread_id = ?",
        arrayOf(thread.accountId, thread.id),
      )
      thread.messages.forEach { message ->
        insertWithOnConflict(
          "mail_messages",
          null,
          ContentValues().apply {
            put("account_id", message.accountId)
            put("id", message.id)
            put("thread_id", message.threadId)
            put("sender", message.sender)
            put("recipients", message.recipients)
            put("subject", message.subject)
            put("snippet", message.snippet)
            put("body", message.body)
            put("html_body", message.htmlBody)
            put("received_at", message.receivedAtEpochMillis)
            put("label_ids", message.labelIds.joinToString(LABEL_SEPARATOR))
            put("is_unread", message.isUnread.asInt())
            put("is_starred", message.isStarred.asInt())
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
      }
    }
  }

  private fun activeSyncGeneration(accountId: String): String? = database.readable.query(
    "mail_accounts",
    arrayOf(SYNC_STATE_COLUMN, SYNC_GENERATION_COLUMN),
    "id = ?",
    arrayOf(accountId),
    null,
    null,
    null,
    "1",
  ).use { cursor ->
    if (!cursor.moveToFirst()) return@use null
    val state = cursor.getString(0)
    if (state == SYNC_STATE_SYNCING || state == SYNC_STATE_WAITING_FOR_NETWORK) cursor.stringOrNull(1) else null
  }

  private fun threadGeneration(accountId: String, threadId: String): String? = database.readable.query(
    "mail_threads",
    arrayOf(SYNC_GENERATION_COLUMN),
    "account_id = ? AND id = ?",
    arrayOf(accountId, threadId),
    null,
    null,
    null,
    "1",
  ).use { cursor -> if (cursor.moveToFirst()) cursor.stringOrNull(0) else null }

  private fun isArchivedLocally(accountId: String, threadId: String): Boolean = localFlag(
    accountId = accountId,
    threadId = threadId,
    column = "archived_locally",
  )

  private fun isReadLaterLocally(accountId: String, threadId: String): Boolean = localFlag(
    accountId = accountId,
    threadId = threadId,
    column = "read_later_locally",
  )

  private fun isRetainedLocally(accountId: String, threadId: String): Boolean =
    isArchivedLocally(accountId, threadId) || isReadLaterLocally(accountId, threadId)

  private fun localFlag(accountId: String, threadId: String, column: String): Boolean = database.readable.query(
    "mail_threads",
    arrayOf(column),
    "account_id = ? AND id = ?",
    arrayOf(accountId, threadId),
    null,
    null,
    null,
    "1",
  ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) != 0 }

  private fun removeStaleThreadsFromGeneration(accountId: String, generation: String) {
    val staleThreadIds = database.readable.query(
      "mail_threads",
      arrayOf("id"),
      "account_id = ? AND archived_locally = 0 AND read_later_locally = 0 AND ($SYNC_GENERATION_COLUMN IS NULL OR $SYNC_GENERATION_COLUMN != ?)",
      arrayOf(accountId, generation),
      null,
      null,
      null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    if (staleThreadIds.isEmpty()) return
    database.transaction {
      for (threadId in staleThreadIds) {
        delete("mail_messages", "account_id = ? AND thread_id = ?", arrayOf(accountId, threadId))
        delete("mail_threads", "account_id = ? AND id = ?", arrayOf(accountId, threadId))
      }
    }
  }

  private fun deleteThread(accountId: String, threadId: String) {
    database.transaction {
      delete("mail_messages", "account_id = ? AND thread_id = ?", arrayOf(accountId, threadId))
      delete("mail_threads", "account_id = ? AND id = ?", arrayOf(accountId, threadId))
    }
  }

  private fun Cursor.mailAccount() = MailAccount(
    id = getString(getColumnIndexOrThrow("id")),
    email = getString(getColumnIndexOrThrow("email")),
    displayName = stringOrNull(getColumnIndexOrThrow("display_name")),
    lastSyncedAtEpochMillis = longOrNull(getColumnIndexOrThrow("last_synced_at")),
    syncState = when (getString(getColumnIndexOrThrow(SYNC_STATE_COLUMN))) {
      SYNC_STATE_SYNCING -> MailSyncState.SYNCING
      SYNC_STATE_WAITING_FOR_NETWORK -> MailSyncState.WAITING_FOR_NETWORK
      SYNC_STATE_ERROR -> MailSyncState.ERROR
      else -> MailSyncState.IDLE
    },
    syncProcessedThreads = getInt(getColumnIndexOrThrow(SYNC_PROCESSED_COLUMN)),
    syncError = stringOrNull(getColumnIndexOrThrow(SYNC_ERROR_COLUMN)),
  )

  private fun Cursor.mailLabel() = MailLabel(
    accountId = getString(getColumnIndexOrThrow("account_id")),
    id = getString(getColumnIndexOrThrow("id")),
    name = getString(getColumnIndexOrThrow("name")),
    type = getString(getColumnIndexOrThrow("type")),
  )

  private fun Cursor.mailThread() = MailThread(
    accountId = getString(getColumnIndexOrThrow("account_id")),
    id = getString(getColumnIndexOrThrow("id")),
    subject = getString(getColumnIndexOrThrow("subject")),
    snippet = getString(getColumnIndexOrThrow("snippet")),
    lastMessageAtEpochMillis = getLong(getColumnIndexOrThrow("last_message_at")),
    messageCount = getInt(getColumnIndexOrThrow("message_count")),
    isInInbox = getInt(getColumnIndexOrThrow("in_inbox")) != 0,
    isUnread = getInt(getColumnIndexOrThrow("is_unread")) != 0,
    isStarred = getInt(getColumnIndexOrThrow("is_starred")) != 0,
    isReadLater = getInt(getColumnIndexOrThrow("read_later_locally")) != 0,
  )

  private fun Cursor.mailMessage() = MailMessage(
    accountId = getString(getColumnIndexOrThrow("account_id")),
    id = getString(getColumnIndexOrThrow("id")),
    threadId = getString(getColumnIndexOrThrow("thread_id")),
    sender = getString(getColumnIndexOrThrow("sender")),
    recipients = getString(getColumnIndexOrThrow("recipients")),
    subject = getString(getColumnIndexOrThrow("subject")),
    snippet = getString(getColumnIndexOrThrow("snippet")),
    body = getString(getColumnIndexOrThrow("body")),
    htmlBody = stringOrNull(getColumnIndexOrThrow("html_body")),
    receivedAtEpochMillis = getLong(getColumnIndexOrThrow("received_at")),
    labelIds = getString(getColumnIndexOrThrow("label_ids")).split(LABEL_SEPARATOR).filter(String::isNotBlank).toSet(),
    isUnread = getInt(getColumnIndexOrThrow("is_unread")) != 0,
    isStarred = getInt(getColumnIndexOrThrow("is_starred")) != 0,
  )

  private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
  private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
  private fun Boolean.asInt(): Int = if (this) 1 else 0

  private val Mailbox.gmailQuery: String
    get() = when (this) {
      Mailbox.INBOX -> "in:inbox"
      Mailbox.UNREAD -> "in:inbox is:unread"
      Mailbox.READ_LATER -> ""
      Mailbox.STARRED -> "is:starred"
      Mailbox.ALL_MAIL -> "in:archive"
    }

  private companion object {
    const val LABEL_SEPARATOR = "\u001f"
    const val INITIAL_SYNC_QUERY = "in:inbox"
    const val INITIAL_SYNC_PAGE_SIZE = 50
    const val THREAD_FETCH_CONCURRENCY = 4
    const val SYNC_STATE_COLUMN = "sync_state"
    const val SYNC_PROCESSED_COLUMN = "sync_processed_threads"
    const val SYNC_ERROR_COLUMN = "sync_error"
    const val SYNC_PAGE_TOKEN_COLUMN = "sync_page_token"
    const val SYNC_START_HISTORY_COLUMN = "sync_start_history_id"
    const val SYNC_GENERATION_COLUMN = "sync_generation"
    const val SYNC_STATE_IDLE = "idle"
    const val SYNC_STATE_SYNCING = "syncing"
    const val SYNC_STATE_WAITING_FOR_NETWORK = "waiting_for_network"
    const val SYNC_STATE_ERROR = "error"

    val ACCOUNT_COLUMNS = arrayOf(
      "id",
      "email",
      "display_name",
      "last_synced_at",
      SYNC_STATE_COLUMN,
      SYNC_PROCESSED_COLUMN,
      SYNC_ERROR_COLUMN,
    )
    val LABEL_COLUMNS = arrayOf("account_id", "id", "name", "type")
    val THREAD_COLUMNS = arrayOf(
      "account_id",
      "id",
      "subject",
      "snippet",
      "last_message_at",
      "message_count",
      "in_inbox",
      "is_unread",
      "is_starred",
      "read_later_locally",
    )
    val MESSAGE_COLUMNS = arrayOf(
      "account_id",
      "id",
      "thread_id",
      "sender",
      "recipients",
      "subject",
      "snippet",
      "body",
      "html_body",
      "received_at",
      "label_ids",
      "is_unread",
      "is_starred",
    )
  }
}
