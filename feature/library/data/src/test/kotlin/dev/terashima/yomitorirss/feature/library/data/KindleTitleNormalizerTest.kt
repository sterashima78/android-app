package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KindleTitleNormalizerTest {
  @Test
  fun `購入済みKindle本のJapanese Edition末尾を削除する`() {
    assertEquals(
      "Example Book",
      normalizeKindleBookTitle("  Example Book (Japanese Edition)  "),
    )
  }

  @Test
  fun `Japanese Editionだけのタイトルは空にしない`() {
    assertEquals(
      "(Japanese Edition)",
      normalizeKindleBookTitle("(Japanese Edition)"),
    )
  }

  @Test
  fun `Kindle取り込み時に正規化したタイトルを保存する`() = withRepository { repository, database ->
    repository.importAmazonLibraryJson(
      LibrarySource.KINDLE,
      """
        {
          "format":"kindle-library-export",
          "version":1,
          "books":[{
            "asin":"B000000001",
            "title":"Example Book (Japanese Edition)",
            "authors":[],
            "coverUrl":null,
            "series":null
          }]
        }
      """.trimIndent(),
    )

    assertEquals("Example Book", storedTitle(database, "B000000001"))
  }

  @Test
  fun `既存の購入済みKindle本もsnapshot時に正規化して保存する`() = withRepository { repository, database ->
    repository.snapshot()
    insertBook(
      database = database,
      sourceId = "B000000001",
      title = "Existing Book (Japanese Edition)",
    )

    val book = repository.snapshot().books.single()

    assertEquals("Existing Book", book.title)
    assertEquals("Existing Book", storedTitle(database, "B000000001"))
  }

  @Test
  fun `Personal Documentの同名末尾は変更しない`() = withRepository { repository, database ->
    repository.snapshot()
    val sourceId = "PDOC:0123456789ABCDEF0123456789ABCDEF"
    insertBook(
      database = database,
      sourceId = sourceId,
      title = "Personal Note (Japanese Edition)",
    )

    repository.snapshot()

    assertEquals("Personal Note (Japanese Edition)", storedTitle(database, sourceId))
  }

  private fun withRepository(
    block: suspend (DefaultLibraryRepository, DatabaseConnection) -> Unit,
  ) = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) = Unit
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    val database = DatabaseConnection(helper)
    try {
      block(DefaultLibraryRepository(database), database)
    } finally {
      helper.close()
    }
  }

  private fun insertBook(
    database: DatabaseConnection,
    sourceId: String,
    title: String,
  ) {
    database.writable.insertOrThrow(
      "library_items",
      null,
      ContentValues().apply {
        put("source", LibrarySource.KINDLE.name)
        put("source_id", sourceId)
        put("title", title)
        put("authors", "[]")
        put("synced_at", 1L)
      },
    )
  }

  private fun storedTitle(database: DatabaseConnection, sourceId: String): String =
    database.readable.rawQuery(
      "SELECT title FROM library_items WHERE source = ? AND source_id = ?",
      arrayOf(LibrarySource.KINDLE.name, sourceId),
    ).use { cursor ->
      check(cursor.moveToFirst()) { "test book not found" }
      cursor.getString(0)
    }
}
