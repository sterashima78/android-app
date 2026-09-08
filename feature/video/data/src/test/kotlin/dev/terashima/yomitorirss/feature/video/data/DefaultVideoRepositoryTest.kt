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
import dev.terashima.yomitorirss.feature.video.VideoFolder
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultVideoRepositoryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var smb: FakeSmbMediaFileAccess
  private lateinit var profiles: FakeSmbConnectionProfileRepository
  private lateinit var repository: DefaultVideoRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) = ensureVideoSchema(db)
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    smb = FakeSmbMediaFileAccess(
      mutableListOf(
        SmbMediaFile(
          serverId = "server-1",
          share = "media",
          rootPath = "videos",
          path = "videos\\movie.mp4",
          name = "movie.mp4",
          size = 1234L,
          modifiedAtEpochMillis = 100L,
        ),
      ),
    )
    profiles = FakeSmbConnectionProfileRepository(
      mutableListOf(
        SmbConnectionProfile(
          id = "server-1",
          name = "Test NAS",
          host = "nas.example.invalid",
          username = "reader",
          credentialConfigured = true,
        ),
      ),
    )
    repository = DefaultVideoRepository(
      database = DatabaseConnection(helper),
      httpClient = UnusedHttpClient,
      smbMediaFileAccess = smb,
      smbConnectionProfiles = profiles,
      webExtractorClient = AndroidWebVideoExtractorClient { null },
    )
    repository.saveSmbSource(
      VideoSmbSource(
        id = "source-1",
        serverId = "server-1",
        share = "media",
        rootPath = "videos",
      ),
    )
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `SMB同期は設定した場所の動画をcatalogへ投影し消えたファイルを削除する`() = runBlocking {
    assertEquals(1, repository.refreshSmb())
    val item = repository.items().single()
    assertEquals("movie", item.title)
    assertEquals(1234L, item.sizeBytes)
    assertEquals("video/mp4", item.mimeType)
    assertTrue("mp4" in smb.requestedExtensions)
    assertEquals(SmbMediaLocation("server-1", "media", "videos"), smb.requestedLocations.single())

    smb.files.clear()
    assertEquals(0, repository.refreshSmb())
    assertTrue(repository.items().isEmpty())
  }

  @Test
  fun `SMB再同期しても既存動画の再生位置を維持する`() = runBlocking {
    repository.refreshSmb()
    val item = repository.items().single()
    repository.updatePlayback(item.id, positionMs = 20_000L, durationMs = 100_000L)

    assertEquals(1, repository.refreshSmb())

    val refreshed = repository.items().single()
    val playback = refreshed.playbackState!!
    assertEquals(item.id, refreshed.id)
    assertEquals(20_000L, playback.positionMs)
    assertEquals(100_000L, playback.durationMs)
    assertFalse(playback.completed)
  }

  @Test
  fun `旧SMB動画IDの再生位置はshare付きIDへ初回再同期時に引き継ぐ`() = runBlocking {
    val legacySourceId = legacySmbVideoSourceId("server-1", "videos\\movie.mp4")
    val legacyVideoId = stableVideoId(VideoSource.SMB, legacySourceId)
    helper.writableDatabase.insertOrThrow(
      "video_items",
      null,
      ContentValues().apply {
        put("id", legacyVideoId)
        put("source", VideoSource.SMB.name)
        put("source_id", legacySourceId)
        put("title", "movie")
        putNull("page_url")
        putNull("thumbnail_url")
        putNull("duration_ms")
        put("size_bytes", 1234L)
        put("mime_type", "video/mp4")
        put("updated_at", 100L)
      },
    )
    helper.writableDatabase.insertOrThrow(
      "video_playback_state",
      null,
      ContentValues().apply {
        put("video_id", legacyVideoId)
        put("position_ms", 33_000L)
        put("duration_ms", 100_000L)
        put("last_played_at", 999L)
        put("completed", 0)
      },
    )

    assertEquals(1, repository.refreshSmb())

    val refreshed = repository.items().single()
    assertNotEquals(legacyVideoId, refreshed.id)
    assertEquals(33_000L, refreshed.playbackState!!.positionMs)
    assertEquals(100_000L, refreshed.playbackState!!.durationMs)
  }

  @Test
  fun `削除済み接続を参照するSMB同期場所は無視して有効な場所だけ同期する`() = runBlocking {
    repository.saveSmbSource(
      VideoSmbSource(
        id = "dangling-source",
        serverId = "deleted-server",
        share = "other",
        rootPath = "movies",
      ),
    )

    assertEquals(1, repository.refreshSmb())

    assertEquals(
      listOf(SmbMediaLocation("server-1", "media", "videos")),
      smb.requestedLocations,
    )
    assertEquals(2, repository.smbSources().size)
  }

  @Test
  fun `SMB同期場所は追加更新削除できる`() {
    val created = repository.saveSmbSource(
      VideoSmbSource(
        id = "",
        serverId = "server-1",
        share = "archive",
        rootPath = "films",
      ),
    )
    assertTrue(created.id.isNotBlank())
    assertEquals(2, repository.smbSources().size)

    repository.saveSmbSource(created.copy(rootPath = "films\\new"))
    assertTrue(repository.smbSources().any { it.id == created.id && it.rootPath == "films\\new" })

    repository.deleteSmbSource(created.id)
    assertEquals(listOf("source-1"), repository.smbSources().map(VideoSmbSource::id))
  }

  @Test
  fun `保存状態は再生状態と独立して保存解除できる`() = runBlocking {
    repository.refreshSmb()
    val item = repository.items().single()

    repository.saveVideo(item.id)
    repository.updatePlayback(item.id, positionMs = 25_000L, durationMs = 100_000L)

    val saved = repository.items().single()
    assertNotNull(saved.savedState)
    assertNull(saved.savedState!!.folderId)
    assertEquals(25_000L, saved.playbackState!!.positionMs)

    repository.removeSavedVideo(item.id)

    val unsaved = repository.items().single()
    assertNull(unsaved.savedState)
    assertEquals(25_000L, unsaved.playbackState!!.positionMs)
  }

  @Test
  fun `保存動画はフォルダへ移動できフォルダ削除後は未分類へ戻る`() = runBlocking {
    repository.refreshSmb()
    val item = repository.items().single()
    val folder = repository.saveFolder(VideoFolder(id = "", name = "映画"))

    repository.saveVideo(item.id, folder.id)
    assertEquals(folder.id, repository.items().single().savedState!!.folderId)

    repository.saveFolder(folder.copy(name = "長編映画"))
    assertEquals("長編映画", repository.folders().single().name)
    assertEquals(folder.id, repository.items().single().savedState!!.folderId)

    repository.deleteFolder(folder.id)

    assertTrue(repository.folders().isEmpty())
    val saved = repository.items().single().savedState
    assertNotNull(saved)
    assertNull(saved!!.folderId)
  }

  @Test
  fun `フォルダ名は大文字小文字を無視して重複できない`() {
    repository.saveFolder(VideoFolder(id = "", name = "Favorites"))

    val result = runCatching {
      repository.saveFolder(VideoFolder(id = "", name = "favorites"))
    }

    assertTrue(result.isFailure)
    assertEquals(1, repository.folders().size)
  }

  @Test
  fun `Web抽出ルールのCookie共有opt-inを保存復元できる`() {
    val saved = repository.saveExtractorRule(
      WebVideoExtractorRule(
        id = "",
        urlPattern = "https://example.invalid/*",
        playbackExtractorCode = "async () => ({})",
        updatedAtEpochMillis = 0L,
        shareCookiesForPlayback = true,
      ),
    )

    val restored = repository.extractorRules().single { it.id == saved.id }
    assertTrue(restored.shareCookiesForPlayback)

    repository.saveExtractorRule(restored.copy(shareCookiesForPlayback = false))
    assertFalse(repository.extractorRules().single { it.id == saved.id }.shareCookiesForPlayback)
  }

  @Test
  fun `既存Web抽出ルールschemaにはCookie共有列をOFFで追加する`() {
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

    val columnNames = db.rawQuery("PRAGMA table_info(video_web_extractor_rules)", null).use { cursor ->
      val nameColumn = cursor.getColumnIndexOrThrow("name")
      buildSet { while (cursor.moveToNext()) add(cursor.getString(nameColumn)) }
    }
    assertTrue("share_cookies_for_playback" in columnNames)
    val value = db.rawQuery(
      "SELECT share_cookies_for_playback FROM video_web_extractor_rules WHERE id = ?",
      arrayOf("legacy-rule"),
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      cursor.getInt(0)
    }
    assertEquals(0, value)
  }

  @Test
  fun `再生率95パーセント以上で視聴済みになり手動解除しても位置を維持する`() = runBlocking {
    repository.refreshSmb()
    val item = repository.items().single()

    repository.updatePlayback(item.id, positionMs = 949L, durationMs = 1000L)
    assertFalse(repository.items().single().playbackState!!.completed)

    repository.updatePlayback(item.id, positionMs = 950L, durationMs = 1000L)
    val completed = repository.items().single().playbackState!!
    assertTrue(completed.completed)
    assertEquals(950L, completed.positionMs)

    repository.setCompleted(item.id, completed = false)
    val restored = repository.items().single().playbackState!!
    assertFalse(restored.completed)
    assertEquals(950L, restored.positionMs)
    assertEquals(1000L, restored.durationMs)
  }
}

private class FakeSmbMediaFileAccess(
  val files: MutableList<SmbMediaFile>,
) : SmbMediaFileAccess {
  var requestedExtensions: Set<String> = emptySet()
    private set
  val requestedLocations = mutableListOf<SmbMediaLocation>()

  override suspend fun listMediaFiles(
    location: SmbMediaLocation,
    extensions: Set<String>,
  ): List<SmbMediaFile> {
    requestedLocations += location
    requestedExtensions = extensions
    return files.filter { file ->
      file.serverId == location.serverId && file.share == location.share && file.rootPath == location.rootPath
    }
  }

  override fun openMediaFile(
    location: SmbMediaLocation,
    path: String,
  ): SmbMediaReadHandle = error("not used by repository tests")
}

private class FakeSmbConnectionProfileRepository(
  val profiles: MutableList<SmbConnectionProfile>,
) : SmbConnectionProfileRepository {
  override suspend fun connectionProfiles(): List<SmbConnectionProfile> = profiles.toList()

  override suspend fun saveConnectionProfile(
    profile: SmbConnectionProfile,
    password: String?,
  ): SmbConnectionProfile = error("not used")

  override suspend fun deleteConnectionProfile(profileId: String) = error("not used")

  override suspend fun libraryLocations(): List<SmbLibraryLocation> = error("not used")

  override suspend fun saveLibraryLocation(location: SmbLibraryLocation): SmbLibraryLocation = error("not used")

  override suspend fun deleteLibraryLocation(serverId: String) = error("not used")
}

private object UnusedHttpClient : HttpClient {
  override suspend fun execute(request: HttpRequest): HttpResponse = error("HTTP is not used by these tests")
}
