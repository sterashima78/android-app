package dev.terashima.yomitorirss.feature.video.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.library.SmbMediaFile
import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.library.SmbMediaReadHandle
import kotlinx.coroutines.runBlocking
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
class DefaultVideoRepositoryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var smb: FakeSmbMediaFileAccess
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
          path = "videos\\movie.mp4",
          name = "movie.mp4",
          size = 1234L,
          modifiedAtEpochMillis = 100L,
        ),
      ),
    )
    repository = DefaultVideoRepository(
      database = DatabaseConnection(helper),
      httpClient = UnusedHttpClient,
      smbMediaFileAccess = smb,
      webExtractorClient = AndroidWebVideoExtractorClient { null },
    )
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `SMB同期は動画をcatalogへ投影し消えたファイルを削除する`() = runBlocking {
    assertEquals(1, repository.refreshSmb())
    val item = repository.items().single()
    assertEquals("movie", item.title)
    assertEquals(1234L, item.sizeBytes)
    assertEquals("video/mp4", item.mimeType)
    assertTrue("mp4" in smb.requestedExtensions)

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

  override suspend fun listMediaFiles(extensions: Set<String>): List<SmbMediaFile> {
    requestedExtensions = extensions
    return files.toList()
  }

  override fun openMediaFile(serverId: String, path: String): SmbMediaReadHandle =
    error("not used by repository tests")
}

private object UnusedHttpClient : HttpClient {
  override suspend fun execute(request: HttpRequest): HttpResponse = error("HTTP is not used by these tests")
}
