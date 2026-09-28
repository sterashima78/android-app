package dev.terashima.yomitorirss.feature.library.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dev.terashima.yomitorirss.core.aiinference.withAiBackgroundInference
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryBookKey
import dev.terashima.yomitorirss.feature.library.LibraryMetadataOrganizer
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationAiTaskController
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationAiTaskKind
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationAiTaskReference
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationAiTaskSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationAiTaskState
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggestion
import dev.terashima.yomitorirss.feature.library.LibraryRepository
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySeriesReorganizationResult
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.organizationKey
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WorkManagerLibraryOrganizationAiTaskController(
  context: Context,
) : LibraryOrganizationAiTaskController {
  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)
  private val taskStore = LibraryOrganizationAiTaskStore(appContext)

  override suspend fun enqueueSuggestion(book: LibraryBook): String =
    enqueue(
      operation = LibraryOrganizationAiOperation.SUGGEST,
      key = book.organizationKey(),
      seriesName = null,
    )

  override suspend fun enqueueSeriesReorganization(book: LibraryBook): String =
    enqueue(
      operation = LibraryOrganizationAiOperation.REORGANIZE_SERIES,
      key = book.organizationKey(),
      seriesName = book.series?.name?.trim()?.takeIf(String::isNotBlank),
    )

  override suspend fun snapshot(requestId: String): LibraryOrganizationAiTaskSnapshot {
    val id = runCatching { UUID.fromString(requestId) }.getOrNull()
      ?: return LibraryOrganizationAiTaskSnapshot(
        state = LibraryOrganizationAiTaskState.FAILED,
        error = "蔵書AIタスクIDが不正です",
      )
    val info = withContext(Dispatchers.IO) { workManager.getWorkInfoById(id).get() }
      ?: return LibraryOrganizationAiTaskSnapshot(
        state = LibraryOrganizationAiTaskState.FAILED,
        error = "蔵書AIタスクが見つかりません",
      )
    return when (info.state) {
      WorkInfo.State.ENQUEUED,
      WorkInfo.State.BLOCKED -> LibraryOrganizationAiTaskSnapshot(LibraryOrganizationAiTaskState.QUEUED)
      WorkInfo.State.RUNNING -> LibraryOrganizationAiTaskSnapshot(LibraryOrganizationAiTaskState.RUNNING)
      WorkInfo.State.SUCCEEDED -> LibraryOrganizationAiTaskSnapshot(
        state = LibraryOrganizationAiTaskState.SUCCEEDED,
        suggestion = taskStore.suggestion(requestId) ?: decodeSuggestion(info),
        seriesResult = taskStore.seriesResult(requestId) ?: decodeSeriesResult(info),
      )
      WorkInfo.State.FAILED -> LibraryOrganizationAiTaskSnapshot(
        state = LibraryOrganizationAiTaskState.FAILED,
        error = info.outputData.getString(KEY_ERROR),
      )
      WorkInfo.State.CANCELLED -> LibraryOrganizationAiTaskSnapshot(LibraryOrganizationAiTaskState.CANCELLED)
    }
  }

  override suspend fun recoverableTask(): LibraryOrganizationAiTaskReference? {
    val reference = taskStore.reference() ?: return null
    val uuid = runCatching { UUID.fromString(reference.requestId) }.getOrNull()
    if (uuid == null) {
      taskStore.deleteIfMatches(reference.requestId)
      return null
    }
    val lookup = runCatching {
      withContext(Dispatchers.IO) { workManager.getWorkInfoById(uuid).get() }
    }
    if (lookup.isFailure) return reference
    if (lookup.getOrNull() == null) {
      taskStore.deleteIfMatches(reference.requestId)
      return null
    }
    return reference
  }

  override suspend fun dismiss(requestId: String) {
    taskStore.deleteIfMatches(requestId)
  }

  private suspend fun enqueue(
    operation: LibraryOrganizationAiOperation,
    key: LibraryBookKey,
    seriesName: String?,
  ): String {
    val request = OneTimeWorkRequestBuilder<LibraryOrganizationAiWorker>()
      .setInputData(
        workDataOf(
          KEY_OPERATION to operation.name,
          KEY_SOURCE to key.source.name,
          KEY_SOURCE_ID to key.sourceId,
        ),
      )
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
      .build()
    val requestId = request.id.toString()
    taskStore.replace(
      requestId = requestId,
      operation = operation,
      key = key,
      seriesName = seriesName,
    )
    try {
      workManager.enqueue(request).await()
    } catch (error: Throwable) {
      taskStore.deleteIfMatches(requestId)
      throw error
    }
    return requestId
  }

  private fun decodeSuggestion(info: WorkInfo): LibraryOrganizationSuggestion? {
    val tags = info.outputData.getString(KEY_TAGS) ?: return null
    val collections = info.outputData.getString(KEY_COLLECTIONS) ?: return null
    return LibraryOrganizationSuggestion(
      tagNames = JSONArray(tags).toStringList(),
      collectionNames = JSONArray(collections).toStringList(),
      reason = info.outputData.getString(KEY_REASON)?.takeIf(String::isNotBlank),
    )
  }

  private fun decodeSeriesResult(info: WorkInfo): LibrarySeriesReorganizationResult? {
    val total = info.outputData.getInt(KEY_TOTAL, -1)
    if (total < 0) return null
    return LibrarySeriesReorganizationResult(
      total = total,
      updated = info.outputData.getInt(KEY_UPDATED, 0),
      failed = info.outputData.getInt(KEY_FAILED, 0),
    )
  }
}

class LibraryOrganizationAiWorker(
  appContext: Context,
  params: WorkerParameters,
  private val organizationRepository: LibraryOrganizationRepository,
  private val libraryRepository: LibraryRepository,
  private val suggester: LibraryOrganizationSuggester,
) : CoroutineWorker(appContext, params) {
  private val taskStore = LibraryOrganizationAiTaskStore(appContext)

  override suspend fun doWork(): Result = withAiBackgroundInference {
    if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) {
      return@withAiBackgroundInference Result.retry()
    }
    val operation = inputData.getString(KEY_OPERATION)
      ?.let { value -> LibraryOrganizationAiOperation.entries.firstOrNull { it.name == value } }
      ?: return@withAiBackgroundInference Result.failure()
    val source = inputData.getString(KEY_SOURCE)
      ?.let { value -> LibrarySource.entries.firstOrNull { it.name == value } }
      ?: return@withAiBackgroundInference Result.failure()
    val sourceId = inputData.getString(KEY_SOURCE_ID)
      ?: return@withAiBackgroundInference Result.failure()
    val key = LibraryBookKey(source, sourceId)

    setForeground(createForegroundInfo(operation))
    try {
      LocalAiBackgroundTaskGate.withPermit(LocalAiBackgroundTaskPriority.NORMAL) {
        if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) {
          return@withPermit Result.retry()
        }
        when (operation) {
          LibraryOrganizationAiOperation.SUGGEST -> suggest(key)
          LibraryOrganizationAiOperation.REORGANIZE_SERIES -> reorganizeSeries(key)
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      Result.failure(workDataOf(KEY_ERROR to error.userMessage()))
    }
  }

  private suspend fun suggest(key: LibraryBookKey): Result {
    val library = libraryRepository.snapshot()
    val allBooks = library.books + library.hiddenBooks
    val book = allBooks.firstOrNull { it.organizationKey() == key }
      ?: return Result.failure(workDataOf(KEY_ERROR to "蔵書が見つかりません"))
    val snapshot = organizationRepository.snapshot()
    val suggestion = suggester.suggest(
      book = book,
      existingTags = snapshot.tags.map { it.name },
      existingCollections = snapshot.collections.map { it.name },
      seriesContext = seriesOrganizationContextFor(
        book = book,
        books = allBooks,
        organizationSnapshot = snapshot,
      ),
    )
    taskStore.writeSuggestion(id.toString(), suggestion)
    return Result.success()
  }

  private suspend fun reorganizeSeries(key: LibraryBookKey): Result {
    val library = libraryRepository.snapshot()
    val allBooks = library.books + library.hiddenBooks
    val selected = allBooks.firstOrNull { it.organizationKey() == key }
      ?: return Result.failure(workDataOf(KEY_ERROR to "蔵書が見つかりません"))
    val series = selected.series
      ?: return Result.failure(workDataOf(KEY_ERROR to "シリーズ情報が設定されていません"))
    val targets = allBooks.filter { sameSeriesForBackground(series, it.series) }
    val result = LibraryMetadataOrganizer(organizationRepository, suggester)
      .reorganizeSeries(targets)
    taskStore.writeSeriesResult(id.toString(), result)
    return Result.success()
  }

  private fun createForegroundInfo(operation: LibraryOrganizationAiOperation): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "蔵書のAI整理", NotificationManager.IMPORTANCE_LOW).apply {
        description = "蔵書のAI整理をバックグラウンドで実行している間に表示します"
        setShowBadge(false)
      },
    )
    val text = when (operation) {
      LibraryOrganizationAiOperation.SUGGEST -> "蔵書の整理候補を生成しています"
      LibraryOrganizationAiOperation.REORGANIZE_SERIES -> "シリーズをAIで再整理しています"
    }
    val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("蔵書をAIで整理しています")
      .setContentText(text)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let { intent ->
      PendingIntent.getActivity(
        applicationContext,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }?.let(builder::setContentIntent)
    return ForegroundInfo(
      NOTIFICATION_ID_BASE + (id.hashCode() and 0x3fff),
      builder.build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
  }
}

private class LibraryOrganizationAiTaskStore(context: Context) {
  private val file = File(context.noBackupFilesDir, FILE_NAME)

  fun replace(
    requestId: String,
    operation: LibraryOrganizationAiOperation,
    key: LibraryBookKey,
    seriesName: String?,
  ) = synchronized(LibraryOrganizationAiTaskStore::class.java) {
    write(
      JSONObject()
        .put("requestId", requestId)
        .put("operation", operation.name)
        .put("source", key.source.name)
        .put("sourceId", key.sourceId)
        .apply { seriesName?.let { put("seriesName", it) } },
    )
  }

  fun writeSuggestion(
    requestId: String,
    suggestion: LibraryOrganizationSuggestion,
  ) = synchronized(LibraryOrganizationAiTaskStore::class.java) {
    val json = readJsonFor(requestId) ?: return@synchronized
    json
      .put("tags", JSONArray(suggestion.tagNames))
      .put("collections", JSONArray(suggestion.collectionNames))
      .apply { suggestion.reason?.let { put("reason", it) } }
    write(json)
  }

  fun writeSeriesResult(
    requestId: String,
    result: LibrarySeriesReorganizationResult,
  ) = synchronized(LibraryOrganizationAiTaskStore::class.java) {
    val json = readJsonFor(requestId) ?: return@synchronized
    json
      .put("total", result.total)
      .put("updated", result.updated)
      .put("failed", result.failed)
    write(json)
  }

  fun reference(): LibraryOrganizationAiTaskReference? = synchronized(LibraryOrganizationAiTaskStore::class.java) {
    val json = readJson() ?: return@synchronized null
    val requestId = json.optString("requestId").takeIf(String::isNotBlank)
      ?: return@synchronized null
    val operation = json.optString("operation")
      .let { saved -> LibraryOrganizationAiOperation.entries.firstOrNull { it.name == saved } }
      ?: return@synchronized null
    val source = json.optString("source")
      .let { saved -> LibrarySource.entries.firstOrNull { it.name == saved } }
      ?: return@synchronized null
    val sourceId = json.optString("sourceId").takeIf(String::isNotBlank)
      ?: return@synchronized null
    LibraryOrganizationAiTaskReference(
      requestId = requestId,
      kind = when (operation) {
        LibraryOrganizationAiOperation.SUGGEST -> LibraryOrganizationAiTaskKind.SUGGESTION
        LibraryOrganizationAiOperation.REORGANIZE_SERIES -> LibraryOrganizationAiTaskKind.SERIES_REORGANIZATION
      },
      bookKey = LibraryBookKey(source, sourceId),
      seriesName = json.optString("seriesName").takeIf(String::isNotBlank),
    )
  }

  fun suggestion(requestId: String): LibraryOrganizationSuggestion? =
    synchronized(LibraryOrganizationAiTaskStore::class.java) {
      val json = readJsonFor(requestId) ?: return@synchronized null
      if (!json.has("tags") || !json.has("collections")) return@synchronized null
      LibraryOrganizationSuggestion(
        tagNames = json.getJSONArray("tags").toStringList(),
        collectionNames = json.getJSONArray("collections").toStringList(),
        reason = json.optString("reason").takeIf(String::isNotBlank),
      )
    }

  fun seriesResult(requestId: String): LibrarySeriesReorganizationResult? =
    synchronized(LibraryOrganizationAiTaskStore::class.java) {
      val json = readJsonFor(requestId) ?: return@synchronized null
      if (!json.has("total")) return@synchronized null
      LibrarySeriesReorganizationResult(
        total = json.optInt("total", 0),
        updated = json.optInt("updated", 0),
        failed = json.optInt("failed", 0),
      )
    }

  fun deleteIfMatches(requestId: String) = synchronized(LibraryOrganizationAiTaskStore::class.java) {
    val json = readJson()
    if (json == null || json.optString("requestId") == requestId) file.delete()
  }

  private fun readJsonFor(requestId: String): JSONObject? {
    val json = readJson() ?: return null
    return json.takeIf { it.optString("requestId") == requestId }
  }

  private fun readJson(): JSONObject? = runCatching { JSONObject(file.readText()) }.getOrNull()

  private fun write(json: JSONObject) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, "${file.name}.tmp")
    temporary.writeText(json.toString())
    if (!temporary.renameTo(file)) {
      file.writeText(json.toString())
      temporary.delete()
    }
  }

  private companion object {
    const val FILE_NAME = "library-organization-ai-task.json"
  }
}

private enum class LibraryOrganizationAiOperation {
  SUGGEST,
  REORGANIZE_SERIES,
}

private fun sameSeriesForBackground(left: LibrarySeries, right: LibrarySeries?): Boolean {
  right ?: return false
  val leftId = left.id?.trim()?.takeIf(String::isNotEmpty)
  val rightId = right.id?.trim()?.takeIf(String::isNotEmpty)
  if (leftId != null && rightId != null) return leftId.equals(rightId, ignoreCase = true)
  val leftName = left.name.trim()
  val rightName = right.name.trim()
  return leftName.isNotEmpty() &&
    rightName.isNotEmpty() &&
    leftName.equals(rightName, ignoreCase = true)
}

private fun JSONArray.toStringList(): List<String> = buildList {
  for (index in 0 until length()) add(getString(index))
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?.take(MAX_ERROR_CHARS)
    ?: javaClass.simpleName

private const val KEY_OPERATION = "library_organization_ai_operation"
private const val KEY_SOURCE = "library_organization_ai_source"
private const val KEY_SOURCE_ID = "library_organization_ai_source_id"
private const val KEY_TAGS = "library_organization_ai_tags"
private const val KEY_COLLECTIONS = "library_organization_ai_collections"
private const val KEY_REASON = "library_organization_ai_reason"
private const val KEY_TOTAL = "library_organization_ai_total"
private const val KEY_UPDATED = "library_organization_ai_updated"
private const val KEY_FAILED = "library_organization_ai_failed"
private const val KEY_ERROR = "library_organization_ai_error"
private const val CHANNEL_ID = "library_ai_organization"
private const val NOTIFICATION_ID_BASE = 13_500
private const val MAX_ERROR_CHARS = 500
