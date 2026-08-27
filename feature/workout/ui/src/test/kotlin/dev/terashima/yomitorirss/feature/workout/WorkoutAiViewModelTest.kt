package dev.terashima.yomitorirss.feature.workout

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
  fun `ChatGPTを選択すると明示したproviderだけでメニュー提案する`() = runTest(dispatcher) {
    val settingsRepository = FakeSettingsRepository()
    val advisor = RecordingAdvisor()
    val today = LocalDate.now().toString()
    val reader = object : WorkoutReader {
      override suspend fun load(): WorkoutSnapshot = newWorkoutSnapshot(today)
    }
    val viewModel = WorkoutAiViewModel(
      workoutReader = reader,
      settingsRepository = settingsRepository,
      advisor = advisor,
    )
    advanceUntilIdle()

    viewModel.setProvider(WorkoutAiProvider.CHATGPT)
    advanceUntilIdle()
    viewModel.requestMenuSuggestion()
    advanceUntilIdle()

    assertEquals(listOf(WorkoutAiProvider.CHATGPT), advisor.providers)
    assertTrue(advisor.prompts.single().contains("今日行うメニューを提案"))
    assertEquals("回答", viewModel.state.value.response)
  }

  @Test
  fun `要求時にWorkoutReaderを再読込して最新セットを使う`() = runTest(dispatcher) {
    var loads = 0
    val today = LocalDate.now().toString()
    val reader = object : WorkoutReader {
      override suspend fun load(): WorkoutSnapshot {
        loads += 1
        return newWorkoutSnapshot(today).copy(
          today = WorkoutDay(
            date = today,
            sets = listOf(
              WorkoutSet(
                id = "latest",
                exerciseId = "push-up",
                exerciseName = "腕立て伏せ",
                unit = WorkoutUnit.REPS,
                type = WorkoutExerciseType.REPS,
                amount = 15,
                recordedAt = "${today}T08:00:00+09:00",
              ),
            ),
          ),
        )
      }
    }
    val advisor = RecordingAdvisor()
    val viewModel = WorkoutAiViewModel(reader, FakeSettingsRepository(), advisor)
    advanceUntilIdle()

    viewModel.requestPostWorkoutReview()
    advanceUntilIdle()

    assertEquals(1, loads)
    assertTrue(advisor.prompts.single().contains("腕立て伏せ: 15回"))
  }

  private class RecordingAdvisor : WorkoutAiAdvisor {
    val providers = mutableListOf<WorkoutAiProvider>()
    val prompts = mutableListOf<String>()

    override suspend fun generate(provider: WorkoutAiProvider, prompt: String): String {
      providers += provider
      prompts += prompt
      return "回答"
    }
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
