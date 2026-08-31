package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import dev.terashima.yomitorirss.feature.library.PreparedLibraryBook
import dev.terashima.yomitorirss.feature.library.SmbBookFormat
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationStatus
import dev.terashima.yomitorirss.feature.library.SmbServerSettings
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmbMetadataNormalizationQueueTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase
  private lateinit var connection: DatabaseConnection
  private lateinit var repository: DefaultSmbMetadataNormalizationRepository
  private lateinit var coverFile: File

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(
        version = libraryDatabaseSchema.migrations.maxOfOrNull { it.targetVersion } ?: 1,
        contributions = listOf(libraryDatabaseSchema),
      ),
    )
    connection = DatabaseConnection(database)
    DefaultLibraryRepository(connection).snapshot()
    coverFile = File(context.cacheDir, "normalization-test-cover.jpg").apply {
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }
    repository = DefaultSmbMetadataNormalizationRepository(connection, NoOpSmbLibraryRepository)
  }

  @After
  fun tearDown() {
    coverFile.delete()
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `却下済み書籍は次回一括解析の対象にならない`() = runBlocking {
    val book = insertSmbBook(sourceId = "reject-book", fileName = "scan_001.cbz")
    repository.startBatch(listOf(book))
    val item = repository.claimNext()!!
    repository.saveGeneratedCandidate(item, "架空本.cbz", proposal("架空本"))
    repository.rejectCandidate(book.sourceId)

    assertEquals(
      SmbMetadataNormalizationStatus.REJECTED,
      repository.batchSnapshot()!!.items.single().status,
    )
    val error = assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.startBatch(listOf(book)) }
    }
    assertTrue(error.message.orEmpty().contains("未確定"))
  }

  @Test
  fun `却下済み書籍は明示的な再解析で確定を解除してキューへ戻せる`() = runBlocking {
    val book = insertSmbBook(sourceId = "retry-rejected-book", fileName = "scan_rejected.cbz")
    repository.startBatch(listOf(book))
    val item = repository.claimNext()!!
    val firstProposal = proposal("架空再解析本")
    repository.saveGeneratedCandidate(item, "架空再解析本.cbz", firstProposal)
    repository.rejectCandidate(book.sourceId)

    repository.retryCandidate(book.sourceId, "著者表記を再確認")

    assertEquals(0, normalizationDecisionCount(book.sourceId))
    assertEquals(
      SmbMetadataNormalizationStatus.QUEUED,
      repository.batchSnapshot()!!.items.single().status,
    )
    val retried = repository.claimNext()!!
    assertEquals(book.sourceId, retried.sourceId)
    assertEquals(firstProposal, retried.previousProposal)
    assertEquals("著者表記を再確認", retried.supplementalContext)
  }

  @Test
  fun `未確認と保留の候補は前回提案を保持して再解析できる`() = runBlocking {
    val book = insertSmbBook(sourceId = "review-retry-book", fileName = "scan_review.cbz")
    repository.startBatch(listOf(book))
    val first = repository.claimNext()!!
    val firstProposal = proposal("最初の候補")
    repository.saveGeneratedCandidate(first, "最初の候補.cbz", firstProposal)

    repository.retryCandidate(book.sourceId, "タイトルの副題を確認")
    var retried = repository.batchSnapshot()!!.items.single()
    assertEquals(SmbMetadataNormalizationStatus.QUEUED, retried.status)
    assertNull(retried.proposedFileName)
    assertEquals(firstProposal, retried.proposal)

    val second = repository.claimNext()!!
    assertEquals(firstProposal, second.previousProposal)
    assertEquals("タイトルの副題を確認", second.supplementalContext)
    val secondProposal = proposal("二回目の候補")
    repository.saveGeneratedCandidate(second, "二回目の候補.cbz", secondProposal)
    repository.deferCandidate(book.sourceId)
    repository.retryCandidate(book.sourceId)

    retried = repository.batchSnapshot()!!.items.single()
    assertEquals(SmbMetadataNormalizationStatus.QUEUED, retried.status)
    assertNull(retried.proposedFileName)
    assertEquals(secondProposal, retried.proposal)
    val third = repository.claimNext()!!
    assertEquals(secondProposal, third.previousProposal)
    assertNull(third.supplementalContext)
  }

  @Test
  fun `過去バッチで却下した書籍もレビューに残り最新バッチへ再解析できる`() = runBlocking {
    val rejectedBook = insertSmbBook(sourceId = "historical-rejected-book", fileName = "scan_old.cbz")
    repository.startBatch(listOf(rejectedBook))
    val rejectedClaim = repository.claimNext()!!
    val rejectedProposal = proposal("過去の候補")
    repository.saveGeneratedCandidate(rejectedClaim, "過去の候補.cbz", rejectedProposal)
    repository.rejectCandidate(rejectedBook.sourceId)

    val currentBook = insertSmbBook(sourceId = "current-book", fileName = "scan_current.cbz")
    repository.startBatch(listOf(rejectedBook, currentBook))
    val beforeRetry = repository.batchSnapshot()!!
    val historical = beforeRetry.items.single { it.sourceId == rejectedBook.sourceId }
    assertEquals(SmbMetadataNormalizationStatus.REJECTED, historical.status)
    assertTrue(historical.batchId != beforeRetry.batchId)

    repository.retryCandidate(rejectedBook.sourceId)

    val afterRetry = repository.batchSnapshot()!!
    val requeued = afterRetry.items.single { it.sourceId == rejectedBook.sourceId }
    assertEquals(afterRetry.batchId, requeued.batchId)
    assertEquals(SmbMetadataNormalizationStatus.QUEUED, requeued.status)
    assertNull(requeued.proposedFileName)
    assertEquals(rejectedProposal, requeued.proposal)
    assertEquals(0, normalizationDecisionCount(rejectedBook.sourceId))
    assertEquals(SmbMetadataNormalizationBatchStatus.RUNNING, afterRetry.status)
  }

  @Test
  fun `確定書誌情報はlibrary itemsの同期キャッシュが書き戻されても表示に合成される`() = runBlocking {
    val book = insertSmbBook(sourceId = "apply-book", fileName = "scan_002.cbz")
    repository.startBatch(listOf(book))
    val item = repository.claimNext()!!
    val proposal = proposal("正規化タイトル")
    repository.saveGeneratedCandidate(item, "scan_002.cbz", proposal)
    repository.applyCandidate(book.sourceId, "scan_002.cbz", proposal)

    connection.writable.update(
      "library_items",
      ContentValues().apply {
        put("title", "scan_002")
        put("authors", "[]")
      },
      "source = ? AND source_id = ?",
      arrayOf(LibrarySource.SMB.name, book.sourceId),
    )

    val stored = SmbMetadataAwareLibraryRepository(connection).snapshot().books.single()
    assertEquals("正規化タイトル", stored.title)
    assertEquals(listOf("架空著者"), stored.authors)
    assertEquals("架空出版社", stored.publisher)
  }

  @Test
  fun `反映済み書誌情報は再解析せず編集して再反映できる`() = runBlocking {
    val book = insertSmbBook(sourceId = "edit-applied-book", fileName = "scan_edit.cbz")
    repository.startBatch(listOf(book))
    val item = repository.claimNext()!!
    val initial = proposal("初回の架空タイトル")
    repository.saveGeneratedCandidate(item, "scan_edit.cbz", initial)
    repository.applyCandidate(book.sourceId, "scan_edit.cbz", initial)

    val edited = initial.copy(
      title = "修正後の架空タイトル",
      authors = listOf("架空著者A", "架空著者B"),
      publisher = "修正後の架空出版社",
      publishedDate = "2026-02-03",
      isbn10 = "0000000000",
      isbn13 = "9780000000000",
      seriesName = "修正後の架空シリーズ",
      seriesPosition = 2,
    )
    repository.applyCandidate(book.sourceId, "scan_edit.cbz", edited)

    val updated = repository.batchSnapshot()!!.items.single()
    assertEquals(SmbMetadataNormalizationStatus.APPLIED, updated.status)
    assertEquals(edited.title, updated.proposal?.title)
    assertEquals(edited.authors, updated.proposal?.authors)
    assertEquals(edited.seriesName, updated.proposal?.seriesName)
    assertEquals(edited.seriesPosition, updated.proposal?.seriesPosition)

    val stored = SmbMetadataAwareLibraryRepository(connection).snapshot().books.single()
    assertEquals(edited.title, stored.title)
    assertEquals(edited.authors, stored.authors)
    assertEquals(edited.publisher, stored.publisher)
    assertEquals(edited.publishedDate, stored.publishedDate)
    assertEquals(edited.isbn10, stored.isbn10)
    assertEquals(edited.isbn13, stored.isbn13)
    assertEquals("修正後の架空シリーズ", stored.series?.name)
    assertEquals(2, stored.series?.position)
  }

  @Test
  fun `反映済み書誌情報の編集ではファイル名を変更できない`() = runBlocking {
    val book = insertSmbBook(sourceId = "applied-filename-book", fileName = "scan_name.cbz")
    repository.startBatch(listOf(book))
    val item = repository.claimNext()!!
    val initial = proposal("ファイル名固定の架空本")
    repository.saveGeneratedCandidate(item, "scan_name.cbz", initial)
    repository.applyCandidate(book.sourceId, "scan_name.cbz", initial)

    val error = assertThrows(IllegalArgumentException::class.java) {
      runBlocking {
        repository.applyCandidate(
          book.sourceId,
          "別の架空ファイル名.cbz",
          initial.copy(title = "修正を試みた架空本"),
        )
      }
    }

    assertTrue(error.message.orEmpty().contains("ファイル名"))
    val stored = SmbMetadataAwareLibraryRepository(connection).snapshot().books.single()
    assertEquals("ファイル名固定の架空本", stored.title)
  }

  @Test
  fun `反映済み書誌のシリーズ未変更編集は後続の手動シリーズ変更を上書きしない`() = runBlocking {
    val book = insertSmbBook(sourceId = "manual-series-book", fileName = "scan_series.cbz")
    repository.startBatch(listOf(book))
    val item = repository.claimNext()!!
    val initial = proposal("シリーズ保持の架空本")
    repository.saveGeneratedCandidate(item, "scan_series.cbz", initial)
    repository.applyCandidate(book.sourceId, "scan_series.cbz", initial)

    DefaultLibraryRepository(connection).setBookSeries(
      book,
      LibrarySeries(name = "手動設定した架空シリーズ", position = 7),
    )

    repository.applyCandidate(
      book.sourceId,
      "scan_series.cbz",
      initial.copy(title = "タイトルだけ修正した架空本"),
    )

    val stored = SmbMetadataAwareLibraryRepository(connection).snapshot().books.single()
    assertEquals("タイトルだけ修正した架空本", stored.title)
    assertEquals("手動設定した架空シリーズ", stored.series?.name)
    assertEquals(7, stored.series?.position)
  }

  @Test
  fun `表紙キャッシュが失われた書籍は表紙再取得キューへ戻せる`() = runBlocking {
    val book = insertSmbBook(sourceId = "stale-cover-book", fileName = "scan_003.cbz")
    coverFile.delete()

    repository.startBatch(listOf(book))

    val storedCover = connection.readable.rawQuery(
      "SELECT thumbnail_url FROM library_items WHERE source = ? AND source_id = ?",
      arrayOf(LibrarySource.SMB.name, book.sourceId),
    ).use { cursor ->
      cursor.moveToFirst()
      if (cursor.isNull(0)) null else cursor.getString(0)
    }
    assertNull(storedCover)
    assertEquals(
      SmbMetadataNormalizationStatus.WAITING_FOR_COVER,
      repository.batchSnapshot()!!.items.single().status,
    )
    assertEquals(1, SmbCoverPrefetchQueueStore(connection).enqueueMissing())
  }

  @Test
  fun `候補生成後にファイルrevisionが変わった場合は前回提案を保持して再解析可能な状態へ移す`() = runBlocking {
    val book = insertSmbBook(sourceId = "changed-book", fileName = "scan_004.cbz", modifiedAt = 20L)
    repository.startBatch(listOf(book))
    val item = repository.claimNext()!!
    val proposal = proposal("変更候補")
    repository.saveGeneratedCandidate(item, "scan_004.cbz", proposal)

    connection.writable.update(
      "library_items",
      ContentValues().apply {
        put("info_url", smbInfoUrl("changed-book", "scan_004.cbz", size = 10L, modifiedAt = 21L))
      },
      "source = ? AND source_id = ?",
      arrayOf(LibrarySource.SMB.name, book.sourceId),
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.applyCandidate(book.sourceId, "scan_004.cbz", proposal) }
    }
    assertTrue(error.message.orEmpty().contains("変更されています"))
    val staleCandidate = repository.batchSnapshot()!!.items.single()
    assertEquals(SmbMetadataNormalizationStatus.SKIPPED, staleCandidate.status)
    assertNull(staleCandidate.proposedFileName)
    assertEquals(proposal, staleCandidate.proposal)
  }

  @Test
  fun `再解析では現在のファイルrevisionを保存して処理を再開する`() = runBlocking {
    val book = insertSmbBook(sourceId = "retry-book", fileName = "scan_005.cbz", size = 10L, modifiedAt = 20L)
    repository.startBatch(listOf(book))
    val firstClaim = repository.claimNext()!!
    repository.skip(firstClaim, "ファイルが変更されました")

    connection.writable.update(
      "library_items",
      ContentValues().apply {
        put("info_url", smbInfoUrl("retry-book", "renamed_005.cbz", size = 12L, modifiedAt = 22L))
      },
      "source = ? AND source_id = ?",
      arrayOf(LibrarySource.SMB.name, book.sourceId),
    )

    repository.retryCandidate(book.sourceId)
    val retried = repository.claimNext()!!

    assertEquals("renamed_005.cbz", retried.originalFileName)
    assertEquals(12L, retried.inputSize)
    assertEquals(22L, retried.inputModifiedAt)
  }

  private fun normalizationDecisionCount(sourceId: String): Int = connection.readable.rawQuery(
    "SELECT COUNT(*) FROM $SMB_METADATA_NORMALIZATION_DECISION_TABLE WHERE source_id = ?",
    arrayOf(sourceId),
  ).use { cursor ->
    cursor.moveToFirst()
    cursor.getInt(0)
  }

  private fun insertSmbBook(
    sourceId: String,
    fileName: String,
    size: Long = 10L,
    modifiedAt: Long = 20L,
  ): LibraryBook {
    val infoUrl = smbInfoUrl(sourceId, fileName, size, modifiedAt)
    connection.writable.insertOrThrow(
      "library_items",
      null,
      ContentValues().apply {
        put("source", LibrarySource.SMB.name)
        put("source_id", sourceId)
        put("title", fileName.substringBeforeLast('.'))
        put("authors", JSONArray().toString())
        putNull("publisher")
        putNull("published_date")
        putNull("description")
        putNull("isbn10")
        putNull("isbn13")
        put("thumbnail_url", Uri.fromFile(coverFile).toString())
        put("info_url", infoUrl)
        put("narrators", JSONArray().toString())
        putNull("duration")
        put("synced_at", 1L)
      },
    )
    return LibraryBook(
      source = LibrarySource.SMB,
      sourceId = sourceId,
      title = fileName.substringBeforeLast('.'),
      authors = emptyList(),
      publisher = null,
      publishedDate = null,
      description = null,
      isbn10 = null,
      isbn13 = null,
      thumbnailUrl = Uri.fromFile(coverFile).toString(),
      infoUrl = infoUrl,
    )
  }

  private fun smbInfoUrl(
    sourceId: String,
    fileName: String,
    size: Long,
    modifiedAt: Long,
  ): String = Uri.Builder()
    .scheme("yomitori")
    .authority("smb-book")
    .path("open")
    .appendQueryParameter("sourceId", sourceId)
    .appendQueryParameter("serverId", "test-server")
    .appendQueryParameter("path", "Books\\$fileName")
    .appendQueryParameter("size", size.toString())
    .appendQueryParameter("modified", modifiedAt.toString())
    .appendQueryParameter("format", SmbBookFormat.ZIP.name)
    .build()
    .toString()

  private fun proposal(title: String) = SmbBookMetadataProposal(
    title = title,
    authors = listOf("架空著者"),
    publisher = "架空出版社",
    publishedDate = "2026-01-01",
    isbn10 = null,
    isbn13 = null,
    seriesName = "架空シリーズ",
    seriesPosition = 1,
    confidence = 0.9f,
    reason = "テスト用の架空候補",
  )
}

private object NoOpSmbLibraryRepository : SmbLibraryRepository {
  override suspend fun servers(): List<SmbServerSettings> = emptyList()
  override suspend fun saveServer(settings: SmbServerSettings, password: String?): SmbServerSettings = settings
  override suspend fun deleteServer(serverId: String) = Unit
  override suspend fun sync(): LibrarySyncResult = error("not used")
  override suspend fun renameBook(book: LibraryBook, newFileName: String): LibraryBook = error("not used")
  override suspend fun deleteBook(book: LibraryBook) = error("not used")
  override suspend fun prepareBook(
    book: LibraryBook,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
  ): PreparedLibraryBook = error("not used")
}
