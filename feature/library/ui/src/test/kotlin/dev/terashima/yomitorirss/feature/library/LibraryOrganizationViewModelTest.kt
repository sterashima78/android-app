package dev.terashima.yomitorirss.feature.library

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryOrganizationViewModelTest {
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
  fun `画面再生成後も複数の未保存AI候補を個別に消費する`() = runTest(dispatcher) {
    val first = book("book-1")
    val second = book("book-2")
    val firstSuggestion = suggestion("first")
    val secondSuggestion = suggestion("second")
    val tasks = RecordingLibraryAiTasks(
      references = listOf(
        LibraryOrganizationAiTaskReference(
          requestId = "request-1",
          kind = LibraryOrganizationAiTaskKind.SUGGESTION,
          bookKey = first.organizationKey(),
        ),
        LibraryOrganizationAiTaskReference(
          requestId = "request-2",
          kind = LibraryOrganizationAiTaskKind.SUGGESTION,
          bookKey = second.organizationKey(),
        ),
      ),
      snapshots = mapOf(
        "request-1" to LibraryOrganizationAiTaskSnapshot(
          state = LibraryOrganizationAiTaskState.SUCCEEDED,
          suggestion = firstSuggestion,
        ),
        "request-2" to LibraryOrganizationAiTaskSnapshot(
          state = LibraryOrganizationAiTaskState.SUCCEEDED,
          suggestion = secondSuggestion,
        ),
      ),
    )
    val repository = RecordingOrganizationRepository()
    val viewModel = LibraryOrganizationViewModel(
      repository = repository,
      aiTaskController = tasks,
      batchScheduler = NoOpBatchScheduler,
    )

    runCurrent()

    assertEquals(firstSuggestion, viewModel.state.value.suggestions[first.organizationKey()])
    assertEquals(secondSuggestion, viewModel.state.value.suggestions[second.organizationKey()])

    viewModel.save(
      book = first,
      draft = LibraryOrganizationDraft(
        tagNames = firstSuggestion.tagNames,
        collectionNames = firstSuggestion.collectionNames,
        readingStatus = null,
      ),
    )
    runCurrent()

    assertEquals(listOf("request-1"), tasks.dismissedRequestIds)
    assertTrue(first.organizationKey() !in viewModel.state.value.suggestions)
    assertEquals(secondSuggestion, viewModel.state.value.suggestions[second.organizationKey()])

    viewModel.viewModelScope.cancel()
  }

  private fun book(id: String) = LibraryBook(
    source = LibrarySource.KINDLE,
    sourceId = id,
    title = id,
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )

  private fun suggestion(value: String) = LibraryOrganizationSuggestion(
    tagNames = listOf("tag-$value"),
    collectionNames = listOf("collection-$value"),
    reason = value,
  )
}

private class RecordingLibraryAiTasks(
  private val references: List<LibraryOrganizationAiTaskReference>,
  private val snapshots: Map<String, LibraryOrganizationAiTaskSnapshot>,
) : LibraryOrganizationAiTaskController {
  val dismissedRequestIds = mutableListOf<String>()

  override suspend fun enqueueSuggestion(book: LibraryBook): String =
    error("unexpected enqueue")

  override suspend fun enqueueSeriesReorganization(book: LibraryBook): String =
    error("unexpected enqueue")

  override suspend fun snapshot(requestId: String): LibraryOrganizationAiTaskSnapshot =
    requireNotNull(snapshots[requestId])

  override suspend fun recoverableTasks(): List<LibraryOrganizationAiTaskReference> = references

  override suspend fun dismiss(requestId: String) {
    dismissedRequestIds += requestId
  }
}

private class RecordingOrganizationRepository : LibraryOrganizationRepository {
  override suspend fun snapshot(): LibraryOrganizationSnapshot = LibraryOrganizationSnapshot()

  override suspend fun save(
    book: LibraryBook,
    draft: LibraryOrganizationDraft,
  ) = Unit

  override suspend fun batchSnapshot(): LibraryOrganizationBatchSnapshot? = null

  override suspend fun startBatch(books: List<LibraryBook>): String = "batch"

  override suspend fun pauseBatch() = Unit

  override suspend fun resumeBatch() = Unit

  override suspend fun rejectCandidate(key: LibraryBookKey) = Unit

  override suspend fun retryCandidate(key: LibraryBookKey) = Unit
}

private object NoOpBatchScheduler : LibraryOrganizationBatchScheduler {
  override fun kick() = Unit

  override suspend fun cancel() = Unit

  override fun setResumeOnChargingScheduled(enabled: Boolean) = Unit
}
