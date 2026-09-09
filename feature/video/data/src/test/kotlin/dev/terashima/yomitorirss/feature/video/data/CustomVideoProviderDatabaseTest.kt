package dev.terashima.yomitorirss.feature.video.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CustomVideoProviderDatabaseTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var database: VideoProviderDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
      }

      override fun onCreate(db: SQLiteDatabase) = ensureVideoSchema(db)
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    database = VideoProviderDatabase(DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `custom providerは複数保存できfunction codeを保持する`() {
    val first = database.saveProvider(customProvider("First", "async () => ({ first: true })"))
    val second = database.saveProvider(customProvider("Second", "async () => ({ second: true })"))

    assertNotEquals(first.id, second.id)
    val providers = database.providers().filter { it.type == VideoProviderType.CUSTOM }
    assertEquals(2, providers.size)
    assertEquals(
      setOf("async () => ({ first: true })", "async () => ({ second: true })"),
      providers.mapNotNull { it.functionCode }.toSet(),
    )
  }

  @Test
  fun `custom provider更新はidentityを維持する`() {
    val saved = database.saveProvider(customProvider("Before", "async () => ({ version: 1 })"))

    val updated = database.saveProvider(
      saved.copy(
        name = "After",
        functionCode = "async () => ({ version: 2 })",
      ),
    )

    assertEquals(saved.id, updated.id)
    assertEquals("After", database.providers().single().name)
    assertEquals("async () => ({ version: 2 })", database.providers().single().functionCode)
  }

  @Test
  fun `custom providerはfunction codeなしでは保存しない`() {
    val error = runCatching { database.saveProvider(customProvider("Invalid", "")) }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertTrue(database.providers().isEmpty())
  }

  private fun customProvider(name: String, code: String): VideoProvider = VideoProvider(
    id = "",
    type = VideoProviderType.CUSTOM,
    name = name,
    functionCode = code,
  )
}
