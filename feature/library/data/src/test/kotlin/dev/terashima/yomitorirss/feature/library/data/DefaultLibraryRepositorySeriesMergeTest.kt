package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryBookSeriesUpdate
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultLibraryRepositorySeriesMergeTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase
  private lateinit var repository: DefaultLibraryRepository

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
    repository = DefaultLibraryRepository(DatabaseConnection(database))
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `シリーズの一括更新は取得元をまたいで手動シリーズを保存し除外を解除する`() = runBlocking {
    val kindle = book(LibrarySource.KINDLE, "TEST-KINDLE", "Kindle 1")
    val smb = book(LibrarySource.SMB, "server/book-2.pdf", "File 2")
    repository.clearBookSeries(kindle)
    repository.clearBookSeries(smb)

    repository.setBookSeries(
      listOf(
        LibraryBookSeriesUpdate(kindle, LibrarySeries("統合シリーズ", 1, id = "SOURCE-ID")),
        LibraryBookSeriesUpdate(smb, LibrarySeries("統合シリーズ", 2)),
      ),
    )

    val rows = database.readableDatabase.rawQuery(
      """
        SELECT source, source_id, series_name, series_position
        FROM library_item_series
        ORDER BY series_position
      """.trimIndent(),
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            listOf(
              cursor.getString(0),
              cursor.getString(1),
              cursor.getString(2),
              cursor.getInt(3).toString(),
            ),
          )
        }
      }
    }
    val exclusionCount = database.readableDatabase.rawQuery(
      "SELECT COUNT(*) FROM library_item_series_exclusions",
      null,
    ).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    }

    assertEquals(
      listOf(
        listOf("KINDLE", "TEST-KINDLE", "統合シリーズ", "1"),
        listOf("SMB", "server/book-2.pdf", "統合シリーズ", "2"),
      ),
      rows,
    )
    assertEquals(0, exclusionCount)
  }

  private fun book(
    source: LibrarySource,
    sourceId: String,
    title: String,
  ) = LibraryBook(
    source = source,
    sourceId = sourceId,
    title = title,
    authors = emptyList(),
    publisher = null,
    publishedDate = null,
    description = null,
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = null,
    infoUrl = null,
  )
}
