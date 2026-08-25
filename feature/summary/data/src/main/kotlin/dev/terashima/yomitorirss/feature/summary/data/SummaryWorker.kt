package dev.terashima.yomitorirss.feature.summary.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.aiinference.AiTextInferenceStage
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInference
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInferenceException
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionSettings
import dev.terashima.yomitorirss.feature.summary.SummaryRuntimeDependencies
import dev.terashima.yomitorirss.feature.summary.renderSummaryPrompt
import dev.terashima.yomitorirss.feature.summary.summaryCacheKey
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SummaryWorker(
  appContext: Context,
  params: WorkerParameters,
  private val runtime: SummaryRuntimeDependencies,
  private val database: YomitoriDatabase,
  private val textInference: AiTextInference,
  private val cloudInference: SummaryCloudInference,
  private val executionSettings: SummaryExecutionSettings,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    if (isProviderPaused(executionSettings.currentProvider())) return Result.success()
    setForeground(createForegroundInfo("AIタスクの実行を待っています"))
    return withContext(Dispatchers.IO) {
      database.requeueInterruptedSummaryTasks()
      while (true) {
        currentCoroutineContext().ensureActive()
        val provider = executionSettings.currentProvider()
        if (isProviderPaused(provider)) break
        val candidates = when (provider) {
          SummaryExecutionProvider.LOCAL -> database.listInferenceReadySummaryTasks()
          SummaryExecutionProvider.CHATGPT -> database.listCloudReadySummaryTasks()
        }
        if (candidates.isEmpty()) break
        val highPriorityIds = runtime.bookmarkContentQuery.readLaterContentIds(
          candidates.mapTo(linkedSetOf(), SummaryTaskRecord::articleId),
        )
        val candidate = selectNextSummaryTask(candidates, highPriorityIds) ?: break
        val outcome = when (provider) {
          SummaryExecutionProvider.LOCAL -> {
            var result = SummaryTaskProcessOutcome.CONTINUE
            LocalAiBackgroundTaskGate.withPermit(summaryTaskPriority(candidate, highPriorityIds)) {
              if (isProviderPaused(provider)) return@withPermit
              val task = database.claimSummaryTask(candidate.articleId) ?: return@withPermit
              result = processTask(database, task, provider)
            }
            result
          }
          SummaryExecutionProvider.CHATGPT -> {
            if (isProviderPaused(provider)) break
            val task = database.claimSummaryTask(candidate.articleId, requireInferenceReady = false)
              ?: continue
            processTask(database, task, provider)
          }
        }
        if (outcome == SummaryTaskProcessOutcome.RETRY_WORK) return@withContext Result.retry()
        SummaryQueue.kickContentFetch(applicationContext)
      }
      Result.success()
    }
  }

  private suspend fun processTask(
    database: YomitoriDatabase,
    task: SummaryTaskRecord,
    provider: SummaryExecutionProvider,
  ): SummaryTaskProcessOutcome {
    val article = runtime.articleRepository.findArticle(task.articleId)
    if (article == null) {
      database.failRunningSummaryTask(task.articleId, "記事が見つかりません")
      return SummaryTaskProcessOutcome.CONTINUE
    }
    val summaryPromptStore = SummaryPromptStore(applicationContext)
    try {
      setForeground(createForegroundInfo(article.title))
      requireProviderAvailable(provider)
      val enrichmentContext = runtime.bookmarkEnrichmentRepository.context(task.articleId)
      val cached = if (task.forceRefresh) null else database.findSummary(task.articleId)
      val summaryForMetadata = if (cached != null) {
        cached.summary
      } else {
        val prompt = summaryPromptStore.prompt.value
        when (provider) {
          SummaryExecutionProvider.LOCAL -> {
            val selectedModel = textInference.selectedModel()
              ?: error("要約モデルをダウンロードして選択してください")
            val cacheKey = "${summaryCacheKey(selectedModel.id, prompt, selectedModel.cacheVariant)}:$HIERARCHICAL_SUMMARY_CACHE_VARIANT"
            val preparedContent = database.findPreparedSummaryArticleContent(task.articleId)
              ?: error("記事本文の準備が完了していません")
            val generated = summarizeWithProgress(
              database,
              textInference,
              task.articleId,
              preparedContent.content,
              prompt,
            )
            currentCoroutineContext().ensureActive()
            database.saveSummary(task.articleId, generated, cacheKey)
            generated
          }
          SummaryExecutionProvider.CHATGPT -> {
            val modelId = cloudInference.selectedModelId()
              ?: error("ChatGPT / Codex の利用モデルを選択してください")
            database.updateRunningSummaryTaskProgress(task.articleId, SUMMARY_PROGRESS_CLOUD_GENERATING_SUMMARY)
            val result = cloudInference.generateFromUrl(
              url = article.url,
              prompt = buildCloudSummaryPrompt(article.url, prompt),
            )
            val generated = cleanCloudText(result.text)
            val cacheKey = summaryCacheKey(
              modelId = "chatgpt:$modelId",
              template = prompt,
              variant = CLOUD_WEB_SUMMARY_CACHE_VARIANT,
            )
            currentCoroutineContext().ensureActive()
            database.saveSummary(task.articleId, generated, cacheKey)
            generated
          }
        }
      }

      if (enrichmentContext != null) {
        currentCoroutineContext().ensureActive()
        val promptSuffix = buildBookmarkMetadataCandidateSuffix(
          articleTitle = article.title,
          existingTagNames = enrichmentContext.existingTagNames,
          existingFolderNames = enrichmentContext.existingFolderNames,
        )
        val raw = when (provider) {
          SummaryExecutionProvider.LOCAL -> {
            textInference.selectedModel()
              ?: error("AIメタデータ生成用のモデルをダウンロードして選択してください")
            textInference.summarizeText(
              text = summaryForMetadata,
              prompt = buildBookmarkMetadataPrompt(),
              promptSuffix = promptSuffix,
            )
          }
          SummaryExecutionProvider.CHATGPT -> {
            database.updateRunningSummaryTaskProgress(task.articleId, SUMMARY_PROGRESS_CLOUD_GENERATING_METADATA)
            cloudInference.generate(
              renderSummaryPrompt(buildBookmarkMetadataPrompt(), summaryForMetadata) + promptSuffix,
            ).text
          }
        }
        val generatedMetadata = parseBookmarkMetadataEnrichment(
          raw = raw,
          existingFolderNames = enrichmentContext.existingFolderNames,
        )
        runtime.bookmarkEnrichmentRepository.applyGeneratedMetadata(
          articleId = task.articleId,
          tagNames = generatedMetadata.tags,
          folderName = generatedMetadata.folder,
          replaceExistingTags = task.replaceBookmarkTags,
        )
      }
      database.completeRunningSummaryTask(task.articleId)
      return SummaryTaskProcessOutcome.CONTINUE
    } catch (error: CancellationException) {
      throw error
    } catch (error: SummaryCloudInferenceException) {
      if (provider == SummaryExecutionProvider.CHATGPT && error.retryable) {
        val message = error.message ?: "クラウドAIが一時的に利用できません。自動的に再試行します"
        database.requeueRunningSummaryTaskForRetry(task.articleId, message)
        return SummaryTaskProcessOutcome.RETRY_WORK
      }
      database.failRunningSummaryTask(task.articleId, error.userMessage())
      return SummaryTaskProcessOutcome.CONTINUE
    } catch (error: Throwable) {
      database.failRunningSummaryTask(task.articleId, error.userMessage())
      return SummaryTaskProcessOutcome.CONTINUE
    }
  }

  private fun isProviderPaused(provider: SummaryExecutionProvider): Boolean {
    val state = SummaryQueue.executionState(applicationContext)
    return when (provider) {
      SummaryExecutionProvider.LOCAL -> state.localPaused
      SummaryExecutionProvider.CHATGPT -> state.cloudPaused
    }
  }

  private fun requireProviderAvailable(provider: SummaryExecutionProvider) {
    when (provider) {
      SummaryExecutionProvider.LOCAL ->
        textInference.selectedModel() ?: error("要約モデルをダウンロードして選択してください")
      SummaryExecutionProvider.CHATGPT ->
        check(cloudInference.isAvailable()) { "ChatGPTへ接続し、利用モデルを選択してください" }
    }
  }

  private suspend fun summarizeWithProgress(
    database: YomitoriDatabase,
    textInference: AiTextInference,
    articleId: String,
    articleText: String,
    prompt: String,
  ): String = coroutineScope {
    val hierarchyProgress = AtomicReference<HierarchicalSummaryProgress?>(null)
    val progressCollector = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
      textInference.progress.filterNotNull().collect { progress ->
        when (progress.stage) {
          AiTextInferenceStage.PREPARING_MODEL ->
            database.updateRunningSummaryTaskProgress(articleId, SUMMARY_PROGRESS_PREPARING_MODEL)
          AiTextInferenceStage.GENERATING_RESPONSE -> {
            val stored = hierarchyProgress.get().toStoredProgress()
            database.updateRunningSummaryTaskProgress(articleId, stored.stage, stored.current, stored.total)
          }
        }
      }
    }
    try {
      textInference.summarizeHierarchically(text = articleText, prompt = prompt) { progress ->
        hierarchyProgress.set(progress)
        val stored = progress.toStoredProgress()
        database.updateRunningSummaryTaskProgress(articleId, stored.stage, stored.current, stored.total)
      }
    } finally {
      progressCollector.cancelAndJoin()
    }
  }

  private fun createForegroundInfo(articleTitle: String): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "記事の要約とタグ付け", NotificationManager.IMPORTANCE_LOW).apply {
        description = "AIで記事をバックグラウンド要約・タグ付けしている間に表示します"
        setShowBadge(false)
      },
    )
    val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("記事をAI処理しています")
      .setContentText(articleTitle)
      .setStyle(NotificationCompat.BigTextStyle().bigText(articleTitle))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)?.let { launchIntent ->
      PendingIntent.getActivity(
        applicationContext,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }?.let(notificationBuilder::setContentIntent)
    return ForegroundInfo(
      NOTIFICATION_ID,
      notificationBuilder.build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
  }

  companion object {
    private const val CHANNEL_ID = "article_summary"
    private const val NOTIFICATION_ID = 8766
    private const val CLOUD_WEB_SUMMARY_CACHE_VARIANT = "chatgpt-web-v1"
  }
}

private enum class SummaryTaskProcessOutcome { CONTINUE, RETRY_WORK }

private data class StoredSummaryProgress(val stage: String, val current: Int? = null, val total: Int? = null)

private fun HierarchicalSummaryProgress?.toStoredProgress(): StoredSummaryProgress = when (this?.stage) {
  HierarchicalSummaryProgressStage.CHUNK ->
    StoredSummaryProgress(SUMMARY_PROGRESS_SUMMARIZING_CHUNK, this?.current, this?.total)
  HierarchicalSummaryProgressStage.REDUCTION ->
    StoredSummaryProgress(SUMMARY_PROGRESS_REDUCING_SUMMARY, this?.current, this?.total)
  HierarchicalSummaryProgressStage.FINAL -> StoredSummaryProgress(SUMMARY_PROGRESS_FINALIZING_SUMMARY)
  HierarchicalSummaryProgressStage.DIRECT,
  null -> StoredSummaryProgress(SUMMARY_PROGRESS_GENERATING_SUMMARY)
}

internal fun buildCloudSummaryPrompt(url: String, prompt: String): String {
  val renderedInstruction = prompt.replace(
    "{{article}}",
    "web_search ツールで開いた次のURLの記事本文: $url",
  )
  return """
    web_search ツールを使って、次のURLを直接 open_page して記事本文を読んでください。
    URL: $url

    指定URLを開けない場合は、検索結果の断片・別ページ・事前知識から推測して要約しないでください。
    指定URLを開けた場合だけ、以下の要約指示に従ってください。

    $renderedInstruction
  """.trimIndent()
}

private fun cleanCloudText(value: String): String {
  val result = value
    .substringBefore("<|im_end|>")
    .substringBefore("<end_of_turn>")
    .replace(Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
    .replace(Regex("^要約[:：]?\\s*", RegexOption.IGNORE_CASE), "")
    .trim()
  check(result.isNotBlank()) { "要約結果が空です" }
  return result
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }.mapNotNull(Throwable::message).firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
