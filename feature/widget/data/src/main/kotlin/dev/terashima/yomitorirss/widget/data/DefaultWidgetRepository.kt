package dev.terashima.yomitorirss.feature.widget.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.widget.WidgetArticle
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import java.time.Instant

class DefaultWidgetRepository(
  private val database: YomitoriDatabase,
  private val feedRepository: FeedRepository,
  private val bookmarkRepository: BookmarkRepository,
  private val backupChangeScheduler: BackupChangeScheduler,
  private val sourceSelector: (String) -> Boolean = { true },
) : WidgetRepository {
  override fun listUnreadArticles(): List<WidgetArticle> = database.readableDatabase.rawQuery(
    "SELECT id,url,title,source_title,published_at,source_feed_url FROM articles WHERE read_at IS NULL ORDER BY published_at DESC LIMIT 500",
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        if (!sourceSelector(cursor.getString(5))) continue
        add(
          WidgetArticle(
            id = cursor.getString(0),
            url = cursor.getString(1),
            title = cursor.getString(2),
            sourceTitle = cursor.getString(3),
            publishedAt = cursor.getString(4),
          ),
        )
      }
    }
  }

  override suspend fun markRead(articleId: String) {
    val saved = database.readableDatabase.rawQuery(
      "SELECT saved_at FROM articles WHERE id=? LIMIT 1",
      arrayOf(articleId),
    ).use { cursor ->
      if (!cursor.moveToFirst()) return
      !cursor.isNull(0)
    }
    database.writableDatabase.update(
      "articles",
      values("read_at" to Instant.now().toString()),
      "id=?",
      arrayOf(articleId),
    )
    if (saved) backupChangeScheduler.scheduleAfterChange()
  }

  override suspend fun markReadLater(articleId: String) {
    bookmarkRepository.markReadLater(articleId)
    backupChangeScheduler.scheduleAfterChange()
  }

  override suspend fun refreshFeeds() {
    feedRepository.listFeeds()
      .filter { sourceSelector(it.feedUrl) }
      .forEach { feed ->
        runCatching { feedRepository.refreshFeed(feed) }
      }
  }
}

private fun values(vararg entries: Pair<String, String>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> put(key, value) }
}
