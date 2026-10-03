package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBoundaryOwnershipArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  private val compositionSourceRoot = "app/composition/src/main/java/dev/terashima/yomitorirss"
  private val presentationUiRoot = "app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui"

  @Test
  fun `共有可能なcrash診断は保存前に最終report全体をsanitizeする`() {
    val startupCrashStore = source(
      "app/src/main/java/dev/terashima/yomitorirss/diagnostics/StartupCrashStore.kt",
    )
    val crashRecord = startupCrashStore
      .substringAfter("fun record(context: Context, threadName: String, throwable: Throwable) {")
      .substringBefore("fun peek(context: Context)")
    val processExitRecord = startupCrashStore
      .substringAfter("fun recordRecentProcessExit(application: Application): Boolean")
      .substringBefore("private fun preferences")

    listOf(crashRecord, processExitRecord).forEach { block ->
      assertTrue(
        "shareable diagnostic report must sanitize the completed report before persistence",
        "val report = sanitizeCrashDetails(" in block,
      )
      assertTrue(
        "only the sanitized report variable may be persisted as the shareable report",
        "putString(REPORT_KEY, report)" in block,
      )
    }
    assertFalse(
      "raw throwable text must not be written directly to the shareable report store",
      "putString(REPORT_KEY, throwable.stackTraceToString())" in crashRecord,
    )
  }

  @Test
  fun `Mail初回同期はdurable checkpointからpage continuationを再構築する`() {
    val repository = source(
      "feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/DefaultMailRepository.kt",
    )
    val worker = source(
      "feature/mail/data/src/main/kotlin/dev/terashima/yomitorirss/feature/mail/data/MailSyncWorker.kt",
    )
    val pageSync = repository
      .substringAfter("override suspend fun syncInitialPage(")
      .substringBefore("override fun markInitialSyncWaitingForNetwork")
    val continuation = pageSync.substringAfter("val nextPageToken = page.nextPageToken")
    val complete = repository
      .substringAfter("private fun completeInitialSync(")
      .substringBefore("private data class InitialSyncState")

    assertTrue(
      "stale page work must stop before applying a mismatched checkpoint",
      "if (state.pageToken != expectedPageToken) return MailInitialSyncStep.Stale" in pageSync,
    )
    assertTrue(
      "next durable checkpoint must be stored before returning continuation metadata",
      continuation.indexOf("updateInitialSyncCheckpoint(") in 0 until continuation.indexOf("return MailInitialSyncStep.Continue"),
    )
    assertTrue(
      "stale Worker execution must rebuild continuation from repository state",
      "MailInitialSyncStep.Stale" in worker && "repository.sync(accountId)" in worker,
    )
    assertTrue(
      "retryable network failure must preserve the durable sync and request retry",
      "markInitialSyncWaitingForNetwork(accountId, error.message)" in worker && "Result.retry()" in worker,
    )
    assertTrue(
      "completion must clear page checkpoint and generation",
      "putNull(SYNC_PAGE_TOKEN_COLUMN)" in complete &&
        "putNull(SYNC_GENERATION_COLUMN)" in complete &&
        "put(SYNC_STATE_COLUMN, SYNC_STATE_IDLE)" in complete,
    )
  }

  @Test
  fun `Podcast定刻Workerはterminal実行だけ次回scheduleへ進める`() {
    val worker = source(
      "feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastGenerationWorker.kt",
    )
    val runBlock = worker.substringAfter("private suspend fun runBackgroundWork(): Result {")
      .substringBefore("private fun createForegroundInfo")
    val alreadyRunningCatch = runBlock.substringAfter("catch (error: PodcastGenerationAlreadyRunningException)")
      .substringBefore("catch (error: CancellationException)")
    val cancellationCatch = runBlock.substringAfter("catch (error: CancellationException) {")
      .substringBefore("finally {")

    assertTrue(
      "scheduled pause skip must converge to the next occurrence",
      "scheduleNext = true" in runBlock &&
        "shouldSkipPodcastGeneration(" in runBlock,
    )
    assertTrue(
      "successful scheduled generation must arm the next occurrence",
      "scheduleNext = operation == PodcastGenerationOperation.SCHEDULED_GENERATE" in runBlock,
    )
    assertTrue(
      "already-running scheduled generation must retry the current occurrence",
      "Result.retry()" in alreadyRunningCatch,
    )
    assertFalse(
      "already-running retry must not append the next occurrence",
      "scheduleNext = true" in alreadyRunningCatch,
    )
    assertTrue(
      "cancellation must suppress Worker-owned rescheduling",
      "scheduleNext = false" in cancellationCatch,
    )
    assertTrue(
      "terminal scheduled runs must append via PodcastScheduleController",
      "currentProgram?.let(scheduleController::scheduleNext)" in runBlock,
    )
  }

  @Test
  fun `CalendarはTaskとWorkoutのread capabilityだけを使うread-only projectionである`() {
    val calendarData = source(
      "feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt",
    )
    val calendarBuild = source("feature/calendar/data/build.gradle.kts")
    val manifest = source("app/src/main/AndroidManifest.xml")

    assertTrue("Calendar must depend on TaskReader", "TaskReader" in calendarData)
    assertTrue("Calendar must depend on WorkoutReader", "WorkoutReader" in calendarData)
    assertFalse("Calendar must not depend on TaskRepository command facade", "TaskRepository" in calendarData)
    assertFalse("Calendar must not depend on WorkoutRepository command facade", "WorkoutRepository" in calendarData)
    assertFalse("Calendar must not access application persistence directly", "DatabaseConnection" in calendarData)
    assertFalse("Calendar data must not depend on Task data implementation", ":feature:task:data" in calendarBuild)
    assertFalse("Calendar data must not depend on Workout data implementation", ":feature:workout:data" in calendarBuild)
    assertTrue("Calendar must request read permission", "android.permission.READ_CALENDAR" in manifest)
    assertFalse("Calendar must not request calendar write permission", "android.permission.WRITE_CALENDAR" in manifest)
  }

  @Test
  fun `Workout AI advisorはWorkout dataが所有しUIにはtask controllerだけを渡す`() {
    val advisorPath = "feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutAiAdvisor.kt"
    val advisor = source(advisorPath)
    val workoutBuild = source("feature/workout/data/build.gradle.kts")
    val supportingRuntime = source("$compositionSourceRoot/composition/supporting/AppSupportingRuntimeDependencies.kt")
    val routeComposition = source("$compositionSourceRoot/composition/route/AppSupportingRouteDependencies.kt")

    assertTrue("Workout data must own the AI advisor", File(repositoryRoot, advisorPath).isFile)
    assertFalse(
      "app must not own Workout AI policy",
      File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppWorkoutAiAdvisor.kt").exists(),
    )
    assertTrue("Workout data must depend on provider-neutral inference", ":core:ai-inference" in workoutBuild)
    assertTrue("Workout advisor must implement the feature contract", "WorkoutAiAdvisor" in advisor)
    assertTrue("composition runtime must compose the Workout-owned advisor", "DefaultWorkoutAiAdvisor(" in supportingRuntime)
    assertTrue("route composition must pass only the durable task controller", "taskController = container.workoutAiTaskController" in routeComposition)
    assertFalse("route composition must not receive raw inference", "container.textInference" in routeComposition)
  }

  @Test
  fun `Workout AI taskはpromptをenqueue時に固定せずWorker実行時に最新入力を読む`() {
    val background = source(
      "feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/WorkoutAiBackground.kt",
    )
    val enqueueBlock = background.substringAfter("override suspend fun enqueue(type: WorkoutAiRequestType): String {")
      .substringBefore("override suspend fun snapshot")
    val generateBlock = background.substringAfter("private suspend fun generate(")
      .substringBefore("private fun isPaused")

    assertTrue("Workout AI work input must carry request type metadata", "KEY_REQUEST_TYPE to type.name" in enqueueBlock)
    assertTrue("Workout AI work input must carry selected provider metadata", "KEY_PROVIDER to provider.name" in enqueueBlock)
    assertFalse("Workout AI must not persist a prebuilt prompt in WorkManager Data", "prompt" in enqueueBlock)
    assertTrue("Worker must read the current Workout snapshot at execution time", "workoutReader.load()" in generateBlock)
    assertTrue("Worker must read current settings at execution time", "settingsRepository.loadSettings()" in generateBlock)
    assertTrue("Worker must read current memos at execution time", "settingsRepository.loadMemos(dates)" in generateBlock)
    assertTrue("Worker must read saved reviews at execution time", "reviewRepository.loadAll()" in generateBlock)
    assertTrue("Worker must build the prompt only after reading execution-time inputs", "WorkoutAiPromptBuilder.build(" in generateBlock)
  }

  @Test
  fun `provider neutral ChatGPT text inferenceはOpenAI coreが所有する`() {
    val adapterPath = "core/ai-cloud-openai/src/main/kotlin/dev/terashima/yomitorirss/core/aicloudopenai/ChatGptTextInference.kt"
    val adapter = source(adapterPath)
    val cloudBuild = source("core/ai-cloud-openai/build.gradle.kts")
    val aiComposition = source("$compositionSourceRoot/composition/ai/AppAiCoreRuntimeDependencies.kt")

    assertTrue("OpenAI core must own ChatGptTextInference", File(repositoryRoot, adapterPath).isFile)
    assertFalse(
      "app must not own provider technical text inference",
      File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/ChatGptTextInference.kt").exists(),
    )
    assertTrue("OpenAI core must implement the provider-neutral contract", "AiTextInference" in adapter)
    assertTrue("OpenAI core must depend on ai-inference", ":core:ai-inference" in cloudBuild)
    assertTrue(
      "app composition must consume the provider-owned adapter",
      "dev.terashima.yomitorirss.core.aicloudopenai.ChatGptTextInference" in aiComposition,
    )
  }

  @Test
  fun `feature UIは非対話型AI推論capabilityを参照しない`() {
    val uiSources = File(repositoryRoot, "feature")
      .walkTopDown()
      .onEnter { directory -> directory.name !in setOf("build", ".gradle") }
      .filter { file ->
        file.isFile &&
          file.extension == "kt" &&
          "/ui/src/" in file.invariantSeparatorsPath
      }
      .toList()

    val forbidden = listOf(
      "BackgroundAiTextInference",
      "BackgroundAiStructuredTextInference",
      "requireAiBackgroundInferenceExecution",
      "withAiBackgroundInference",
    )
    val offenders = uiSources.flatMap { file ->
      val source = file.readText()
      forbidden.filter { symbol -> symbol in source }
        .map { symbol -> "${file.relativeTo(repositoryRoot).invariantSeparatorsPath}: $symbol" }
    }

    assertTrue("feature UI must not depend on background inference capabilities: $offenders", offenders.isEmpty())
  }

  @Test
  fun `非対話型AI実行はWorker execution scopeでのみ開始する`() {
    val inferenceScope = source(
      "core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiBackgroundInferenceScope.kt",
    )
    val workoutWorker = source(
      "feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/WorkoutAiBackground.kt",
    )
    val knowledgeWorker = source(
      "feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgePageAiBackground.kt",
    )
    val libraryWorker = source(
      "feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryOrganizationAiBackground.kt",
    )

    assertTrue("AI execution scope must require a CoroutineWorker receiver", "CoroutineWorker.withAiBackgroundInference" in inferenceScope)
    listOf(workoutWorker, knowledgeWorker, libraryWorker).forEach { source ->
      assertTrue("non-interactive AI worker must enter the inference scope", "withAiBackgroundInference" in source)
    }

    val productionGuardOverrides = repositoryRoot.walkTopDown()
      .onEnter { directory -> directory.name !in setOf(".git", ".gradle", "build") }
      .filter { file ->
        file.isFile &&
          file.extension == "kt" &&
          "/src/main/" in file.invariantSeparatorsPath &&
          "override suspend fun validateBackgroundExecution" in file.readText()
      }
      .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
      .sorted()
      .toList()
    assertTrue(
      "production inference adapters must not bypass the base background guard: $productionGuardOverrides",
      productionGuardOverrides.isEmpty(),
    )
  }

  @Test
  fun `providerとnetworkのconcrete integrationはcompositionが所有する`() {
    val appBuild = source("app/build.gradle.kts")
    val compositionBuild = source("app/composition/build.gradle.kts")
    val compositionOnlyDependencies = listOf(
      ":core:ai-cloud-openai",
      ":core:network",
    )

    compositionOnlyDependencies.forEach { dependency ->
      val declaration = "implementation(project(\"$dependency\"))"
      assertFalse(
        "executable app must not depend directly on composition-only integration: $dependency",
        declaration in appBuild,
      )
      assertTrue(
        "app composition must own concrete integration dependency: $dependency",
        declaration in compositionBuild,
      )
    }
  }

  @Test
  fun `Activity result authorization bridgeはplatform packageが所有する`() {
    val authorizationPath = "$compositionSourceRoot/platform/authorization/AuthorizationDependencies.kt"
    val authorization = source(authorizationPath)
    val mailHost = source("$presentationUiRoot/MailRouteHost.kt")
    val libraryHost = source("$presentationUiRoot/LibraryRoute.kt")

    assertTrue("platform package must own authorization bridges", File(repositoryRoot, authorizationPath).isFile)
    assertFalse(
      "root app package must not keep authorization bridge declarations",
      File(repositoryRoot, "app/src/main/java/dev/terashima/yomitorirss/AppAuthorizationDependencies.kt").exists(),
    )
    assertTrue("authorization bridge must own Mail activity-result boundary", "MailAuthorizationDependencies" in authorization)
    assertTrue(
      "Mail presentation host must import the platform authorization boundary",
      "dev.terashima.yomitorirss.platform.authorization.MailAuthorizationOutcome" in mailHost,
    )
    assertTrue(
      "Library presentation host must import the platform authorization boundary",
      "dev.terashima.yomitorirss.platform.authorization.LibraryAuthorizationOutcome" in libraryHost,
    )
  }

  @Test
  fun `startup background compositionはbackground packageが所有する`() {
    val backgroundPath = "$compositionSourceRoot/composition/background/AppBackgroundRuntime.kt"
    val backgroundRuntime = source(backgroundPath)
    val appContainer = source("$compositionSourceRoot/AppContainer.kt")

    assertTrue("background package must own startup composition", File(repositoryRoot, backgroundPath).isFile)
    assertFalse(
      "composition root must not keep startup background runtime",
      File(repositoryRoot, "$compositionSourceRoot/AppBackgroundRuntime.kt").exists(),
    )
    assertTrue(
      "AppContainer must import the packaged background runtime",
      "dev.terashima.yomitorirss.composition.background.AppBackgroundRuntime" in appContainer,
    )
    assertTrue("background runtime must keep startup scheduling", "BookmarkAutoEnrichmentBackfillScheduler.schedule" in backgroundRuntime)
  }

  @Test
  fun `LAN Web server service manifestはWeb dataが所有する`() {
    val appManifest = source("app/src/main/AndroidManifest.xml")
    val webDataManifest = source("feature/web/data/src/main/AndroidManifest.xml")
    val serviceName = "dev.terashima.yomitorirss.feature.web.data.LanWebServerService"

    assertFalse("app manifest must not own the LAN Web Server service", serviceName in appManifest)
    assertTrue("Web data manifest must own the LAN Web Server service", serviceName in webDataManifest)
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
