package dev.terashima.yomitorirss.feature.asset.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultAssetRepositoryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var repository: DefaultAssetRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        assetDatabaseSchema.createSchema(db)
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    repository = DefaultAssetRepository(context, DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `既存の負の金額は概要とカテゴリ設定から除外する`() = runTest {
    insertEntry("2026-08-18", "Asset A", 100L)
    insertEntry("2026-08-19", "Negative Asset", -50L)

    val overview = repository.loadOverview()

    assertEquals(LocalDate.of(2026, 8, 18), overview.latestDate)
    assertEquals(100L, overview.total)
    assertEquals(mapOf("その他" to 100L), overview.latestByCategory)
    assertEquals(listOf("Asset A"), overview.categorySettings.map { it.assetName })
    assertEquals(listOf(LocalDate.of(2026, 8, 18)), overview.history.map { it.date })
  }

  @Test
  fun `MoneyForwardインポートでは負の金額を保存しない`() = runTest {
    val result = repository.importMoneyForwardJson(
      """
        {
          "format": "moneyforward-asset-snapshot",
          "version": 1,
          "date": "2026-08-18",
          "entries": [
            {"name": "Asset A", "amount": 100, "account": "Bank A"},
            {"name": "Negative Asset", "amount": -50, "account": "Bank B"},
            {"name": "Zero Asset", "amount": 0, "account": "Bank C"}
          ]
        }
      """.trimIndent(),
    )

    assertEquals(2, result.rowCount)
    assertEquals(1, result.snapshotCount)
    assertEquals(listOf(0L, 100L), storedAmounts("2026-08-18"))
  }

  @Test
  fun `負の金額だけの再インポートでも既存スナップショットを削除する`() = runTest {
    insertEntry("2026-08-18", "Old Asset", 100L)

    val result = repository.importMoneyForwardJson(
      """
        {
          "format": "moneyforward-asset-snapshot",
          "version": 1,
          "date": "2026-08-18",
          "entries": [
            {"name": "Negative Asset", "amount": -50, "account": "Bank A"}
          ]
        }
      """.trimIndent(),
    )

    assertEquals(0, result.rowCount)
    assertEquals(0, result.snapshotCount)
    assertEquals(emptyList<Long>(), storedAmounts("2026-08-18"))
  }

  private fun insertEntry(date: String, name: String, amount: Long) {
    helper.writableDatabase.execSQL(
      "INSERT INTO asset_entries(snapshot_date,name,amount,account,source) VALUES(?,?,?,?,?)",
      arrayOf<Any?>(date, name, amount, "", "test"),
    )
  }

  private fun storedAmounts(date: String): List<Long> = buildList {
    helper.readableDatabase.rawQuery(
      "SELECT amount FROM asset_entries WHERE snapshot_date=? ORDER BY amount",
      arrayOf(date),
    ).use { cursor ->
      while (cursor.moveToNext()) add(cursor.getLong(0))
    }
  }
}
