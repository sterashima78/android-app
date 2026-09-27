package dev.terashima.yomitorirss.feature.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class KnowledgeViewModelTest {
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
  fun `空の作成要求はAI taskを登録せず入力エラーを表示する`() = runTest(dispatcher) {
    val tasks = RecordingPageAiTasks()
    val viewModel = KnowledgeViewModel(
      repository = FakeKnowledgeRepository(),
      pageAiTasks = tasks,
      scheduleRebuild = {},
    )
    advanceUntilIdle()

    viewModel.createPage()

    assertEquals("作成したい記事の内容を入力してください", viewModel.state.value.message)
    assertFalse(viewModel.state.value.working)
    assertEquals(0, tasks.createCount)
  }

  @Test
  fun `再構築はbuilderを直接実行せずschedulerへ登録する`() = runTest(dispatcher) {
    var scheduled = 0
    val viewModel = KnowledgeViewModel(
      repository = FakeKnowledgeRepository(),
      pageAiTasks = RecordingPageAiTasks(),
      scheduleRebuild = { scheduled += 1 },
    )
    advanceUntilIdle()

    viewModel.rebuild()
    advanceUntilIdle()

    assertEquals(1, scheduled)
    assertFalse(viewModel.state.value.building)
  }
}

private class FakeKnowledgeRepository : KnowledgeRepository {
  override val changes: StateFlow<Long> = MutableStateFlow(0)
  override suspend fun listPages(query: String): List<KnowledgePageSummary> = emptyList()
  override suspend fun findPage(id: String): KnowledgePage? = null
  override suspend fun deletePage(id: String) = Unit
  override suspend fun splitPage(id: String, heading: String): KnowledgePage = page(id)
  override suspend fun mergePages(primaryId: String, secondaryId: String): KnowledgePage = page(primaryId)

  private fun page(id: String) = KnowledgePage(
    id = id,
    title = id,
    bodyMarkdown = "",
    sourceCount = 0,
    generatedAt = "2026-01-01T00:00:00Z",
    editorManaged = true,
    sources = emptyList(),
  )
}

private class RecordingPageAiTasks : KnowledgePageAiTaskController {
  var createCount = 0

  override suspend fun enqueueCreate(request: String, sourcePageId: String?): String {
    createCount += 1
    return "request-1"
  }

  override suspend fun enqueueEdit(pageId: String, instruction: String): String = "request-2"

  override suspend fun snapshot(requestId: String): KnowledgePageAiTaskSnapshot =
    KnowledgePageAiTaskSnapshot(KnowledgePageAiTaskState.FAILED, error = "not expected")
}
