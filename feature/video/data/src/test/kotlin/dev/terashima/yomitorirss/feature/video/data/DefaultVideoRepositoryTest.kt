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
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
