package dev.terashima.yomitorirss.feature.article.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.article.data.network.ArticleContentClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkArticleGatewayTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var database: DatabaseConnection
  private lateinit var gateway: DefaultBookmarkArticleGateway

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          """
            CREATE TABLE articles(
              id TEXT PRIMARY KEY,
              feed_id TEXT,
              external_id TEXT,
              identity_key TEXT,
              url TEXT,
              title TEXT,
              published_at TEXT,
              fetched_at TEXT,
              read_at TEXT,
              source_title TEXT,
              source_feed_url TEXT
            )
          """.trimIndent(),
        )
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    database = DatabaseConnection(helper)
    gateway = DefaultBookmarkArticleGateway(database)
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `共有URLの初回処理はContentを作成して既読にする`() = runBlocking {
    val articleId = gateway.findOrCreateSharedArticle(
      url = "https://example.com/article",
      title = "Example",
      sourceTitle = "Shared",
    )

    helper.readableDatabase.rawQuery(
      "SELECT read_at FROM articles WHERE id=?",
      arrayOf(articleId),
    ).use { cursor ->
      assertEquals(true, cursor.moveToFirst())
      assertNotNull(cursor.getString(0))
    }
  }

  @Test
  fun `共有元にタイトルがない場合はページタイトルを補完する`() = runBlocking {
    val titleGateway = DefaultBookmarkArticleGateway(
      database = database,
      articleContentClient = ArticleContentClient(
        FixedHttpClient(
          """
            <html><head><title>Resolved Page</title></head><body></body></html>
          """.trimIndent(),
        ),
      ),
    )

    val articleId = titleGateway.findOrCreateSharedArticle(
      url = "https://example.com/article",
      title = "example.com",
      sourceTitle = "example.com",
    )

    assertEquals("Resolved Page", storedTitle(articleId))
  }

  @Test
  fun `ページタイトル取得に失敗してもホスト名で保存する`() = runBlocking {
    val fallbackGateway = DefaultBookmarkArticleGateway(
      database = database,
      articleContentClient = ArticleContentClient(FailingHttpClient),
    )

    val articleId = fallbackGateway.findOrCreateSharedArticle(
      url = "https://example.com/article",
      title = "example.com",
      sourceTitle = "example.com",
    )

    assertEquals("example.com", storedTitle(articleId))
  }

  @Test
  fun `既存のホスト名タイトルは再共有時に具体的なタイトルへ更新する`() = runBlocking {
    val fallbackGateway = DefaultBookmarkArticleGateway(
      database = database,
      articleContentClient = ArticleContentClient(FailingHttpClient),
    )
    val first = fallbackGateway.findOrCreateSharedArticle(
      url = "https://example.com/article",
      title = "example.com",
      sourceTitle = "example.com",
    )

    val second = fallbackGateway.findOrCreateSharedArticle(
      url = "https://example.com/article",
      title = "Resolved Page",
      sourceTitle = "example.com",
    )

    assertEquals(first, second)
    assertEquals("Resolved Page", storedTitle(first))
  }

  @Test
  fun `同じ共有URLを再処理してもContentを重複作成しない`() = runBlocking {
    val first = gateway.findOrCreateSharedArticle("https://example.com/article", "Example", "Shared")
    val second = gateway.findOrCreateSharedArticle("https://example.com/article", "Example", "Shared")

    assertEquals(first, second)
    helper.readableDatabase.rawQuery(
      "SELECT COUNT(*) FROM articles WHERE url=?",
      arrayOf("https://example.com/article"),
    ).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, cursor.getInt(0))
    }
  }

  @Test
  fun `import は既存Contentを再利用する`() = runBlocking {
    val shared = gateway.findOrCreateSharedArticle("https://example.com/article", "Example", "Shared")

    val imported = gateway.findOrCreateImportedArticle(
      url = "https://example.com/article",
      title = "Imported",
      sourceTitle = "example.com",
      createdAt = "2026-08-01T00:00:00Z",
      identityPrefix = "html",
    )

    assertEquals(shared, imported)
  }

  private fun storedTitle(articleId: String): String = helper.readableDatabase.rawQuery(
    "SELECT title FROM articles WHERE id=?",
    arrayOf(articleId),
  ).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getString(0)
  }
}

private class FixedHttpClient(
  html: String,
) : HttpClient {
  private val response = HttpResponse(
    statusCode = 200,
    reasonPhrase = "OK",
    finalUrl = "https://example.com/article",
    headers = mapOf("Content-Type" to listOf("text/html; charset=UTF-8")),
    body = html.toByteArray(),
  )

  override suspend fun execute(request: HttpRequest): HttpResponse = response
}

private object FailingHttpClient : HttpClient {
  override suspend fun execute(request: HttpRequest): HttpResponse =
    error("network unavailable")
}
