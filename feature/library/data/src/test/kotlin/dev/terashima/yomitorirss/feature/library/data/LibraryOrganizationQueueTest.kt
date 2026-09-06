package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidateStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationDraft
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggestion
import dev.terashima.yomitorirss.feature.library.LibraryReadingStatus
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryOrganizationQueueTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase
  private lateinit var repository: DefaultLibraryOrganizationRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(
        version = libraryDatabaseSchema.migrations.maxOfOrNull { it.targetVersion } ?: 1,
        contributions = listOf(libraryDatabaseSchema),
      ),
    )
    repository = DefaultLibraryOrganizationRepository(DatabaseConnection(database))
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `生成候補は読書状態を維持して整理情報と候補状態を一度に確定する`() = runBlocking {
    val book = testLibraryBook("apply-book")
    repository.save(
      book,
      LibraryOrganizationDraft(
        tagNames = emptyList(),
        collectionNames = emptyList(),
        readingStatus = LibraryReadingStatus.READING,
      ),
    )
    repository.startBatch(listOf(book))
    val item = repository.claimNextBatchItem()
    assertNotNull(item)

    repository.applyGeneratedSuggestion(
      item = requireNotNull(item),
      book = book,
      suggestion = LibraryOrganizationSuggestion(
        tagNames = listOf("Kotlin"),
        collectionNames = listOf("技術"),
        reason = "test candidate",
      ),
    )

    val organization = repository.snapshot().organizationFor(book)
    assertEquals(listOf("Kotlin"), organization.tags.map { it.name })
    assertEquals(listOf("技術"), organization.collections.map { it.name })
    assertEquals(LibraryReadingStatus.READING, organization.readingStatus)
    val candidate = repository.batchSnapshot()!!.candidates.single()
    assertEquals(LibraryOrganizationCandidateStatus.APPLIED, candidate.status)
    assertEquals(listOf("Kotlin"), candidate.tagNames)
    assertEquals(listOf("技術"), candidate.collectionNames)
    assertEquals("test candidate", candidate.reason)
  }

  @Test
  fun `AI生成中の手動整理を優先して候補を却下する`() = runBlocking {
    val book = testLibraryBook("manual-race-book")
    repository.startBatch(listOf(book))
    val item = requireNotNull(repository.claimNextBatchItem())

    repository.save(
      book,
      LibraryOrganizationDraft(
        tagNames = listOf("手動タグ"),
        collectionNames = listOf("手動コレクション"),
        readingStatus = LibraryReadingStatus.FINISHED,
      ),
    )

    repository.applyGeneratedSuggestion(
      item = item,
      book = book,
      suggestion = LibraryOrganizationSuggestion(
        tagNames = listOf("AIタグ"),
        collectionNames = listOf("AIコレクション"),
        reason = "generated",
      ),
    )

    val organization = repository.snapshot().organizationFor(book)
    assertEquals(listOf("手動タグ"), organization.tags.map { it.name })
    assertEquals(listOf("手動コレクション"), organization.collections.map { it.name })
    assertEquals(LibraryReadingStatus.FINISHED, organization.readingStatus)
    assertEquals(
      LibraryOrganizationCandidateStatus.REJECTED,
      repository.batchSnapshot()!!.candidates.single().status,
    )
  }

  @Test
  fun `退役済みレビュー状態は復活させず却下済みとして読み込む`() = runBlocking {
    val book = testLibraryBook("retired-state-book")
    repository.startBatch(listOf(book))

    setRawCandidateStatus(book.sourceId, "PENDING_REVIEW")
    assertEquals(
      LibraryOrganizationCandidateStatus.REJECTED,
      repository.batchSnapshot()!!.candidates.single().status,
    )

    setRawCandidateStatus(book.sourceId, "DEFERRED")
    assertEquals(
      LibraryOrganizationCandidateStatus.REJECTED,
      repository.batchSnapshot()!!.candidates.single().status,
    )
  }

  @Test
  fun `一括整理は一時停止と再開をDB状態として保持する`() = runBlocking {
    repository.startBatch(listOf(testLibraryBook("pause-book")))

    repository.pauseBatch()
    assertEquals(LibraryOrganizationBatchStatus.PAUSED, repository.batchSnapshot()!!.status)

    repository.resumeBatch()
    assertEquals(LibraryOrganizationBatchStatus.RUNNING, repository.batchSnapshot()!!.status)
  }

  private fun setRawCandidateStatus(sourceId: String, status: String) {
    val db = database.writableDatabase
    val batchId = db.rawQuery(
      "SELECT batch_id FROM library_organization_batches ORDER BY created_at DESC LIMIT 1",
      null,
    ).use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    }
    db.update(
      "library_organization_batch_items",
      ContentValues().apply { put("status", status) },
      "batch_id = ? AND source = ? AND source_id = ?",
      arrayOf(batchId, LibrarySource.KINDLE.name, sourceId),
    )
  }
}

private fun testLibraryBook(sourceId: String): LibraryBook = LibraryBook(
  source = LibrarySource.KINDLE,
  sourceId = sourceId,
  title = "Test Book $sourceId",
  authors = listOf("Test Author"),
  publisher = null,
  publishedDate = null,
  description = null,
  isbn10 = null,
  isbn13 = null,
  thumbnailUrl = null,
  infoUrl = null,
)
