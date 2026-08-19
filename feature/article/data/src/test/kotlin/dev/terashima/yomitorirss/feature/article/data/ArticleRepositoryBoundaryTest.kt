package dev.terashima.yomitorirss.feature.article.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentClassificationSourceQuery
import dev.terashima.yomitorirss.feature.article.ContentRetentionProtectionQuery
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SourceContentTypeOverrides
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArticleRepositoryBoundaryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var database: DatabaseConnection

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE feeds(id TEXT PRIMARY KEY NOT NULL)")
        db.execSQL(
          """
            CREATE TABLE articles(
              id TEXT PRIMARY KEY NOT NULL,
              feed_id TEXT,
              external_id TEXT,
              identity_key TEXT NOT NULL,
              url TEXT NOT NULL,
              title TEXT NOT NULL,
              published_at TEXT NOT NULL,
              fetched_at TEXT NOT NULL,
              read_at TEXT,
              source_title TEXT NOT NULL,
              source_feed_url TEXT NOT NULL,
              content_type TEXT
            )
          """.trimIndent(),
        )
      }
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    database = DatabaseConnection(helper)
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `実効ContentTypeはSource query portから解決する`() = runBlocking {
    helper.writableDatabase.insertOrThrow("feeds", null, ContentValues().apply { put("id", "feed-1") })
    insertArticle(id = "article-1", feedId = "feed-1", readAt = null)
    var requestedSourceIds: Set<String> = emptySet()
    val repository = repository(
      classificationQuery = object : ContentClassificationSourceQuery {
        override suspend fun findOverrides(sourceIds: Set<String>): Map<String, SourceContentTypeOverrides> {
          requestedSourceIds = sourceIds
          return mapOf("feed-1" to SourceContentTypeOverrides(ContentType.COMIC, null))
        }
      },
    )

    val article = repository.findArticle("article-1")

    assertEquals(setOf("feed-1"), requestedSourceIds)
    assertEquals(ContentType.COMIC, article?.effectiveContentType)
  }

  @Test
  fun `cleanupは外部schemaを直接参照せず保護queryの結果を使う`() = runBlocking {
    insertArticle(id = "delete-me", readAt = "2026-01-01T00:00:00Z")
    insertArticle(id = "keep-me", readAt = "2026-01-01T00:00:00Z")
    var requestedCandidateIds: Set<String> = emptySet()
    val repository = repository(
      retentionQuery = object : ContentRetentionProtectionQuery {
        override fun protectedContentIds(contentIds: Set<String>): Set<String> {
          requestedCandidateIds = contentIds
          return setOf("keep-me")
        }
      },
    )

    repository.cleanupExpiredArticles()

    assertEquals(setOf("delete-me", "keep-me"), requestedCandidateIds)
    assertFalse(articleExists("delete-me"))
    assertTrue(articleExists("keep-me"))
  }

  private fun repository(
    classificationQuery: ContentClassificationSourceQuery = object : ContentClassificationSourceQuery {
      override suspend fun findOverrides(sourceIds: Set<String>) = emptyMap<String, SourceContentTypeOverrides>()
    },
    retentionQuery: ContentRetentionProtectionQuery = object : ContentRetentionProtectionQuery {
      override fun protectedContentIds(contentIds: Set<String>) = emptySet<String>()
    },
  ) = DefaultArticleRepository(
    database = database,
    contentClassificationSourceQuery = classificationQuery,
    contentRetentionProtectionQuery = retentionQuery,
  )

  private fun insertArticle(id: String, feedId: String? = null, readAt: String?) {
    helper.writableDatabase.insertOrThrow(
      "articles",
      null,
      ContentValues().apply {
        put("id", id)
        if (feedId == null) putNull("feed_id") else put("feed_id", feedId)
        putNull("external_id")
        put("identity_key", "test:$id")
        put("url", "https://example.com/$id")
        put("title", id)
        put("published_at", "2026-01-01T00:00:00Z")
        put("fetched_at", "2026-01-01T00:00:00Z")
        if (readAt == null) putNull("read_at") else put("read_at", readAt)
        put("source_title", "test")
        put("source_feed_url", "")
        putNull("content_type")
      },
    )
  }

  private fun articleExists(id: String): Boolean = helper.readableDatabase.rawQuery(
    "SELECT 1 FROM articles WHERE id=? LIMIT 1",
    arrayOf(id),
  ).use { it.moveToFirst() }
}
