package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkEnrichmentRepositoryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var repository: DefaultBookmarkEnrichmentRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bookmarks(article_id TEXT PRIMARY KEY, saved_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE tags(id TEXT PRIMARY KEY, name TEXT, normalized_name TEXT UNIQUE, created_at TEXT)")
        db.execSQL("CREATE TABLE article_tags(article_id TEXT, tag_id TEXT, PRIMARY KEY(article_id,tag_id))")
        db.execSQL("CREATE TABLE bookmark_folders(id TEXT PRIMARY KEY, name TEXT, normalized_name TEXT UNIQUE, system_kind TEXT, created_at TEXT)")
        db.execSQL("CREATE TABLE article_folders(article_id TEXT PRIMARY KEY, folder_id TEXT)")
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    repository = DefaultBookmarkEnrichmentRepository(DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `未保存Contentにはenrichment候補を返さない`() = runBlocking {
    assertNull(repository.context("a1"))
  }

  @Test
  fun `保存済み未分類Bookmarkには既存tagとfolder候補を返す`() = runBlocking {
    val db = helper.writableDatabase
    db.execSQL("INSERT INTO bookmarks(article_id,saved_at) VALUES('a1','2026-08-19T00:00:00Z')")
    db.execSQL("INSERT INTO tags(id,name,normalized_name,created_at) VALUES('t1','Android','android','now')")
    db.execSQL("INSERT INTO bookmark_folders(id,name,normalized_name,system_kind,created_at) VALUES('f1','開発','開発',NULL,'now')")

    val context = repository.context("a1")

    assertEquals(listOf("Android"), context?.existingTagNames)
    assertEquals(listOf("開発"), context?.existingFolderNames)
  }

  @Test
  fun `生成metadataの適用はCuration tableだけを更新する`() = runBlocking {
    val db = helper.writableDatabase
    db.execSQL("INSERT INTO bookmarks(article_id,saved_at) VALUES('a1','2026-08-19T00:00:00Z')")
    db.execSQL("INSERT INTO bookmark_folders(id,name,normalized_name,system_kind,created_at) VALUES('f1','開発','開発',NULL,'now')")

    val changed = repository.applyGeneratedMetadata(
      articleId = "a1",
      tagNames = listOf("Kotlin"),
      folderName = "開発",
    )

    assertTrue(changed)
    db.rawQuery("SELECT COUNT(*) FROM article_tags WHERE article_id='a1'", null).use { cursor ->
      cursor.moveToFirst()
      assertEquals(1, cursor.getInt(0))
    }
    db.rawQuery("SELECT folder_id FROM article_folders WHERE article_id='a1'", null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("f1", cursor.getString(0))
    }
    assertFalse(repository.context("a1")?.existingFolderNames.orEmpty().isNotEmpty())
  }

  @Test
  fun `通常enrichmentは既存tagを保持して生成tagを追加する`() = runBlocking {
    val db = helper.writableDatabase
    db.execSQL("INSERT INTO bookmarks(article_id,saved_at) VALUES('a1','2026-08-19T00:00:00Z')")
    db.execSQL("INSERT INTO tags(id,name,normalized_name,created_at) VALUES('old','旧タグ','旧タグ','now')")
    db.execSQL("INSERT INTO article_tags(article_id,tag_id) VALUES('a1','old')")

    val changed = repository.applyGeneratedMetadata(
      articleId = "a1",
      tagNames = listOf("新タグ"),
      folderName = null,
    )

    assertTrue(changed)
    db.rawQuery(
      "SELECT t.name FROM article_tags x JOIN tags t ON t.id=x.tag_id WHERE x.article_id='a1' ORDER BY t.name",
      null,
    ).use { cursor ->
      val names = buildList {
        while (cursor.moveToNext()) add(cursor.getString(0))
      }
      assertEquals(listOf("新タグ", "旧タグ"), names)
    }
  }

  @Test
  fun `refresh enrichmentは生成成功後に既存tagを置き換える`() = runBlocking {
    val db = helper.writableDatabase
    db.execSQL("INSERT INTO bookmarks(article_id,saved_at) VALUES('a1','2026-08-19T00:00:00Z')")
    db.execSQL("INSERT INTO tags(id,name,normalized_name,created_at) VALUES('old','旧タグ','旧タグ','now')")
    db.execSQL("INSERT INTO article_tags(article_id,tag_id) VALUES('a1','old')")

    val changed = repository.applyGeneratedMetadata(
      articleId = "a1",
      tagNames = listOf("新タグ"),
      folderName = null,
      replaceExistingTags = true,
    )

    assertTrue(changed)
    db.rawQuery(
      "SELECT t.name FROM article_tags x JOIN tags t ON t.id=x.tag_id WHERE x.article_id='a1'",
      null,
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("新タグ", cursor.getString(0))
      assertFalse(cursor.moveToNext())
    }
  }
}
