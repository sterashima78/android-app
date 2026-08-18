package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.ContentType
import kotlinx.coroutines.flow.StateFlow

interface FeedRepository {
  val changes: StateFlow<Long>
  suspend fun listFeeds(): List<Feed>
  suspend fun listFolders(): List<FeedFolder>
  suspend fun inspect(input: String): FeedInspection
  suspend fun addFeed(url: String, markExistingArticlesRead: Boolean = false)
  suspend fun renameFeed(feedId: String, name: String)
  suspend fun deleteFeed(feedId: String)
  suspend fun createFolder(name: String)
  suspend fun renameFolder(folderId: String, name: String)
  suspend fun deleteFolder(folderId: String)
  suspend fun moveFeedToFolder(feedId: String, folderId: String?)
  suspend fun setFeedContentType(feedId: String, contentType: ContentType?)
  suspend fun setFolderContentType(folderId: String, contentType: ContentType?)
  suspend fun refreshFeed(feed: Feed)
}
