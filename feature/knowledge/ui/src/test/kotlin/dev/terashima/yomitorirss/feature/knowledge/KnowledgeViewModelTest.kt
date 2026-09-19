package dev.terashima.yomitorirss.feature.knowledge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
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
  fun `空の作成要求はAI処理を開始せず入力エラーを表示する`() = runTest(dispatcher) {
    val creator = RecordingCreator()
    val viewModel = KnowledgeViewModel(
      repository = FakeKnowledgeRepository(),
      builder = FakeBuilder(),
      creator = creator,
      editor = FakeEditor(),
    )
    advanceUntilIdle()

    viewModel.createPage()

    assertEquals("作成したい記事の内容を入力してください", viewModel.state.value.message)
    assertFalse(viewModel.state.value.working)
    assertEquals(0, creator.callCount)
  }

  @Test
  fun `scheduleRebuildがある場合はbuilderを直接実行しない`() = runTest(dispatcher) {
    val builder = FakeBuilder()
    var scheduled = 0
    val viewModel = KnowledgeViewModel(
      repository = FakeKnowledgeRepository(),
      builder = builder,
      creator = RecordingCreator(),
      editor = FakeEditor(),
      scheduleRebuild = { scheduled += 1 },
    )
    advanceUntilIdle()

    viewModel.rebuild()
    advanceUntilIdle()

    assertEquals(1, scheduled)
    assertEquals(0, builder.callCount)
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

private class FakeBuilder : KnowledgeBuilder {
  var callCount = 0
  override suspend fun rebuild(): KnowledgeBuildResult {
    callCount += 1
    return KnowledgeBuildResult(0, 0, 0, 0)
  }
}

private class RecordingCreator : KnowledgePageCreator {
  var callCount = 0
  override suspend fun createPage(request: String, sourcePageId: String?): KnowledgePage {
    callCount += 1
    error("not expected")
  }
}

private class FakeEditor : KnowledgePageEditor {
  override suspend fun editPage(id: String, instruction: String): KnowledgePage =
    error("not expected")
}
