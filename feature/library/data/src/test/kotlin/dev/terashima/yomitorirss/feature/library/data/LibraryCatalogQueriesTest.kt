package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibrarySource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryCatalogQueriesTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase
  private lateinit var connection: DatabaseConnection

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(TEST_DATABASE_NAME)
    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(version = 1, contributions = emptyList()),
    )
    connection = DatabaseConnection(database)
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(TEST_DATABASE_NAME)
  }

  @Test
  fun `単体検索は蔵書スナップショットに依存せずcatalog schemaを初期化して取得する`() {
    assertNull(findLibraryBook(connection, LibrarySource.SMB, "missing"))
    assertTrue(tableExists("library_items"))
    assertTrue(tableExists("library_item_series"))
    assertTrue(tableExists("library_item_series_exclusions"))

    database.writableDatabase.insertOrThrow(
      "library_items",
      null,
      ContentValues().apply {
        put("source", LibrarySource.SMB.name)
        put("source_id", "book-1")
        put("title", "Book One")
        put("authors", "[\"Author A\"]")
        put("publisher", "Publisher")
        put("published_date", "2026-08-22")
        putNull("description")
        putNull("isbn10")
        put("isbn13", "9780000000001")
        put("thumbnail_url", "file:///tmp/cover.jpg")
        put("info_url", "yomitori://smb-book/open?sourceId=book-1")
        put("narrators", "[]")
        putNull("duration")
        put("synced_at", 1L)
      },
    )
    database.writableDatabase.insertOrThrow(
      "library_item_series",
      null,
      ContentValues().apply {
        put("source", LibrarySource.SMB.name)
        put("source_id", "book-1")
        put("series_name", "Series A")
        put("series_position", 3)
        put("updated_at", 2L)
      },
    )
    database.writableDatabase.insertOrThrow(
      "library_item_series_exclusions",
      null,
      ContentValues().apply {
        put("source", LibrarySource.SMB.name)
        put("source_id", "book-1")
        put("updated_at", 3L)
      },
    )

    val book = findLibraryBook(connection, LibrarySource.SMB, "book-1")

    requireNotNull(book)
    assertEquals("Book One", book.title)
    assertEquals(listOf("Author A"), book.authors)
    assertEquals("Publisher", book.publisher)
    assertEquals("9780000000001", book.isbn13)
    assertEquals("Series A", book.series?.name)
    assertEquals(3, book.series?.position)
    assertTrue(book.automaticSeriesExcluded)
  }

  private fun tableExists(name: String): Boolean = database.readableDatabase.rawQuery(
    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
    arrayOf(name),
  ).use { it.moveToFirst() }

  private companion object {
    const val TEST_DATABASE_NAME = "yomitori-rss.db"
  }
}
