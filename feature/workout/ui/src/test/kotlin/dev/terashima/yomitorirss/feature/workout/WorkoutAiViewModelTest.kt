package dev.terashima.yomitorirss.feature.workout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutAiViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `メニュー提案はAIを直接実行せずbackground taskへ登録する`() = runTest(dispatcher) {
    val settingsRepository = FakeSettingsRepository()
    val tasks = RecordingTaskController()
    val viewModel = WorkoutAiViewModel(
      settingsRepository = settingsRepository,
      taskController = tasks,
    )
    advanceUntilIdle()

    viewModel.setProvider(WorkoutAiProvider.CHATGPT)
    advanceUntilIdle()
    viewModel.requestMenuSuggestion()
    advanceUntilIdle()

    assertEquals(WorkoutAiProvider.CHATGPT, settingsRepository.loadSettings().provider)
    assertEquals(listOf(WorkoutAiRequestType.MENU_SUGGESTION), tasks.requestTypes)
    assertEquals("回答", viewModel.state.value.response)
    assertFalse(viewModel.state.value.loading)
  }

  @Test
  fun `background taskの失敗を画面状態へ投影する`() = runTest(dispatcher) {
    val tasks = RecordingTaskController(
      result = WorkoutAiTaskSnapshot(
        state = WorkoutAiTaskState.FAILED,
        error = "生成に失敗しました",
      ),
    )
    val viewModel = WorkoutAiViewModel(
      settingsRepository = FakeSettingsRepository(),
      taskController = tasks,
    )
    advanceUntilIdle()

    viewModel.requestPostWorkoutReview()
    advanceUntilIdle()

    assertEquals("生成に失敗しました", viewModel.state.value.errorMessage)
    assertFalse(viewModel.state.value.loading)
  }

  private class RecordingTaskController(
    private val result: WorkoutAiTaskSnapshot = WorkoutAiTaskSnapshot(
      state = WorkoutAiTaskState.SUCCEEDED,
      response = "回答",
    ),
  ) : WorkoutAiTaskController {
    val requestTypes = mutableListOf<WorkoutAiRequestType>()

    override suspend fun enqueue(type: WorkoutAiRequestType): String {
      requestTypes += type
      return "request-1"
    }

    override suspend fun snapshot(requestId: String): WorkoutAiTaskSnapshot = result
  }

  private class FakeSettingsRepository : WorkoutAiSettingsRepository {
    private var settings = WorkoutAiSettings()
    private val memos = mutableMapOf<String, String>()

    override suspend fun loadSettings(): WorkoutAiSettings = settings

    override suspend fun saveSettings(settings: WorkoutAiSettings) {
      this.settings = settings
    }

    override suspend fun loadMemo(date: String): String = memos[date].orEmpty()

    override suspend fun saveMemo(date: String, memo: String) {
      memos[date] = memo
    }

    override suspend fun loadMemos(dates: Set<String>): Map<String, String> =
      memos.filterKeys { it in dates }
  }
}
