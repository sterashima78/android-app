package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import dev.terashima.yomitorirss.feature.library.PreparedLibraryBook
import dev.terashima.yomitorirss.feature.library.SmbBookFormat
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationStatus
import dev.terashima.yomitorirss.feature.library.SmbServerSettings
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
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
  fun `候補生成後にファイルrevisionが変わった場合は反映しない`() = runBlocking {
    val book = insertSmbBook(sourceId = "changed-book", fileName = "scan_003.cbz", modifiedAt = 20L)
    repository.startBatch(listOf(book))
    val item = repository.claimNext()!!
    val proposal = proposal("変更候補")
    repository.saveGeneratedCandidate(item, "scan_003.cbz", proposal)

    connection.writable.update(
      "library_items",
      ContentValues().apply {
        put("info_url", smbInfoUrl("changed-book", "scan_003.cbz", size = 10L, modifiedAt = 21L))
      },
      "source = ? AND source_id = ?",
      arrayOf(LibrarySource.SMB.name, book.sourceId),
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.applyCandidate(book.sourceId, "scan_003.cbz", proposal) }
    }
    assertTrue(error.message.orEmpty().contains("変更されています"))
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
