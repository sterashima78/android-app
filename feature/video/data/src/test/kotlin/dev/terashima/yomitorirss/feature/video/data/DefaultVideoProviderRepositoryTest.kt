package dev.terashima.yomitorirss.feature.video.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultVideoProviderRepositoryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var connection: DatabaseConnection

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
    connection = DatabaseConnection(helper)
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `provider refreshはcoroutine cancellationを失敗件数へ変換しない`() = runBlocking {
    val database = VideoProviderDatabase(connection)
    val provider = database.saveProvider(
      VideoProvider(
        id = "provider-1",
        type = VideoProviderType.YOUTUBE,
        name = "Provider",
      ),
    )
    database.upsertProviderFeed(
      provider,
      VideoProviderFeed(
        sourceId = CHANNEL_ID,
        title = "Channel",
        sourceUrl = "https://example.invalid/channel",
        videos = emptyList(),
      ),
    )
    val repository = DefaultVideoProviderRepository(connection, CancellingHttpClient)

    val error = runCatching { repository.refreshProviders() }.exceptionOrNull()

    assertTrue(error is CancellationException)
  }

  private object CancellingHttpClient : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse {
      throw CancellationException("cancelled")
    }
  }

  private companion object {
    const val CHANNEL_ID = "UCabcdefghijklmnopqrstuv"
  }
}
