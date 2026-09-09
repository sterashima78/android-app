package dev.terashima.yomitorirss.feature.video.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfile
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfileRepository
import dev.terashima.yomitorirss.feature.library.SmbLibraryLocation
import dev.terashima.yomitorirss.feature.library.SmbMediaFile
import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.library.SmbMediaLocation
import dev.terashima.yomitorirss.feature.library.SmbMediaReadHandle
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebVideoReferrerPathSettingTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var repository: DefaultVideoRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) = ensureVideoSchema(db)
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    repository = DefaultVideoRepository(
      database = DatabaseConnection(helper),
      httpClient = UnusedReferrerSettingHttpClient,
      smbMediaFileAccess = UnusedReferrerSettingSmbAccess,
      smbConnectionProfiles = EmptyReferrerSettingSmbProfiles,
      webExtractorClient = AndroidWebVideoExtractorClient { null },
    )
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `Referer path共有opt-inを保存復元できる`() {
    val saved = repository.saveExtractorRule(
      WebVideoExtractorRule(
        id = "",
        urlPattern = "https://example.invalid/*",
        playbackExtractorCode = "async () => ({})",
        updatedAtEpochMillis = 0L,
        shareReferrerPathForPlayback = true,
      ),
    )

    val restored = repository.extractorRules().single { it.id == saved.id }
    assertTrue(restored.shareReferrerPathForPlayback)

    repository.saveExtractorRule(restored.copy(shareReferrerPathForPlayback = false))
    assertFalse(repository.extractorRules().single { it.id == saved.id }.shareReferrerPathForPlayback)
  }

  @Test
  fun `既存schemaにはReferer path共有列をOFFで追加する`() {
    val db = helper.writableDatabase
    db.execSQL("DROP TABLE video_web_extractor_rules")
    db.execSQL(
      """
        CREATE TABLE video_web_extractor_rules (
          id TEXT PRIMARY KEY NOT NULL,
          url_pattern TEXT NOT NULL,
          title_function TEXT,
          thumbnail_function TEXT,
          playback_function TEXT,
          share_cookies_for_playback INTEGER NOT NULL DEFAULT 0,
          timeout_seconds INTEGER NOT NULL DEFAULT 15,
          updated_at INTEGER NOT NULL
        )
      """.trimIndent(),
    )
    db.insertOrThrow(
      "video_web_extractor_rules",
      null,
      ContentValues().apply {
        put("id", "legacy-rule")
        put("url_pattern", "https://example.invalid/*")
        put("timeout_seconds", 15)
        put("updated_at", 1L)
      },
    )

    ensureVideoSchema(db)

    val columns = db.rawQuery("PRAGMA table_info(video_web_extractor_rules)", null).use { cursor ->
      val nameColumn = cursor.getColumnIndexOrThrow("name")
      buildSet { while (cursor.moveToNext()) add(cursor.getString(nameColumn)) }
    }
    assertTrue("share_referrer_path_for_playback" in columns)
    val value = db.rawQuery(
      "SELECT share_referrer_path_for_playback FROM video_web_extractor_rules WHERE id = ?",
      arrayOf("legacy-rule"),
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      cursor.getInt(0)
    }
    assertEquals(0, value)
  }
}

private object UnusedReferrerSettingHttpClient : HttpClient {
  override suspend fun execute(request: HttpRequest): HttpResponse = error("not used")
}

private object UnusedReferrerSettingSmbAccess : SmbMediaFileAccess {
  override suspend fun listMediaFiles(
    location: SmbMediaLocation,
    extensions: Set<String>,
  ): List<SmbMediaFile> = emptyList()

  override fun openMediaFile(
    location: SmbMediaLocation,
    path: String,
  ): SmbMediaReadHandle = error("not used")
}

private object EmptyReferrerSettingSmbProfiles : SmbConnectionProfileRepository {
  override suspend fun connectionProfiles(): List<SmbConnectionProfile> = emptyList()

  override suspend fun saveConnectionProfile(
    profile: SmbConnectionProfile,
    password: String?,
  ): SmbConnectionProfile = error("not used")

  override suspend fun deleteConnectionProfile(profileId: String) = error("not used")

  override suspend fun libraryLocations(): List<SmbLibraryLocation> = emptyList()

  override suspend fun saveLibraryLocation(location: SmbLibraryLocation): SmbLibraryLocation = error("not used")

  override suspend fun deleteLibraryLocation(serverId: String) = error("not used")
}
