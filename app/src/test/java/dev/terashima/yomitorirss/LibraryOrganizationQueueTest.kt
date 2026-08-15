package dev.terashima.yomitorirss

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidateStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationDraft
import dev.terashima.yomitorirss.feature.library.LibraryReadingStatus
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.organizationKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = YomitoriApplication::class)
class LibraryOrganizationQueueTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase
  private lateinit var repository: DefaultLibraryOrganizationRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    database = YomitoriDatabase.create(context, appDatabaseSchema)
    repository = DefaultLibraryOrganizationRepository(DatabaseConnection(database))
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `一括整理の候補はDBに保持され保留から未確認へ戻せる`() = runBlocking {
    val book = testLibraryBook("queue-book")
    repository.startBatch(listOf(book))
    markCandidateReady(book.sourceId, listOf("Android"), listOf("技術"))

    assertEquals(
      LibraryOrganizationCandidateStatus.PENDING_REVIEW,
      repository.batchSnapshot()!!.candidates.single().status,
    )

    repository.deferCandidate(book.organizationKey())
    assertEquals(
      LibraryOrganizationCandidateStatus.DEFERRED,
      repository.batchSnapshot()!!.candidates.single().status,
    )

    repository.reopenCandidate(book.organizationKey())
    assertEquals(
      LibraryOrganizationCandidateStatus.PENDING_REVIEW,
      repository.batchSnapshot()!!.candidates.single().status,
    )
  }

  @Test
  fun `候補採用は読書状態を維持して整理情報と候補状態を同時に確定する`() = runBlocking {
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
    markCandidateReady(book.sourceId, listOf("Kotlin"), listOf("技術"))

    repository.acceptCandidate(
      book,
      LibraryOrganizationDraft(
        tagNames = listOf("Kotlin"),
        collectionNames = listOf("技術"),
        readingStatus = null,
      ),
    )

    val organization = repository.snapshot().organizationFor(book)
    assertEquals(listOf("Kotlin"), organization.tags.map { it.name })
    assertEquals(listOf("技術"), organization.collections.map { it.name })
    assertEquals(LibraryReadingStatus.READING, organization.readingStatus)
    assertEquals(
      LibraryOrganizationCandidateStatus.APPLIED,
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

  private fun markCandidateReady(
    sourceId: String,
    tags: List<String>,
    collections: List<String>,
  ) {
    val db = database.writableDatabase
    val batchId = db.rawQuery(
      "SELECT batch_id FROM library_organization_batches ORDER BY created_at DESC LIMIT 1",
      null,
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      cursor.getString(0)
    }
    db.update(
      "library_organization_batch_items",
      ContentValues().apply {
        put("status", LibraryOrganizationCandidateStatus.PENDING_REVIEW.name)
        put("tag_names_json", org.json.JSONArray(tags).toString())
        put("collection_names_json", org.json.JSONArray(collections).toString())
        put("reason", "test candidate")
        put("updated_at", 10L)
      },
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
