package dev.terashima.yomitorirss.feature.web.data

import dev.terashima.yomitorirss.feature.web.LanWebArticleItem
import dev.terashima.yomitorirss.feature.web.LanWebContentGateway
import dev.terashima.yomitorirss.feature.web.LanWebFeedItem

internal class LanWebReadModel(
  private val contentGateway: LanWebContentGateway,
) {
  suspend fun loadHome(requestedView: String?): LanWebHomePage {
    val view = requestedView?.takeIf { it in LanWebViews.all } ?: LanWebViews.UNREAD
    return when (view) {
      LanWebViews.SAVED -> LanWebHomePage(
        view = view,
        title = "ブックマーク",
        content = LanWebContent.Articles(
          articles = contentGateway.listSavedArticles(),
          emptyText = "ブックマークはありません。",
        ),
      )
      LanWebViews.READ_LATER -> LanWebHomePage(
        view = view,
        title = "あとで読む",
        content = LanWebContent.Articles(
          articles = contentGateway.listReadLaterArticles(),
          emptyText = "あとで読む記事はありません。",
        ),
      )
      LanWebViews.FEEDS -> LanWebHomePage(
        view = view,
        title = "RSSフィード",
        content = LanWebContent.Feeds(
          feeds = contentGateway.listFeeds(),
        ),
      )
      else -> LanWebHomePage(
        view = LanWebViews.UNREAD,
        title = "RSS未読",
        content = LanWebContent.Articles(
          articles = contentGateway.listUnreadArticles(),
          emptyText = "RSSの未読記事はありません。",
        ),
      )
    }
  }
}

internal data class LanWebHomePage(
  val view: String,
  val title: String,
  val content: LanWebContent,
)

internal sealed interface LanWebContent {
  data class Articles(
    val articles: List<LanWebArticleItem>,
    val emptyText: String,
  ) : LanWebContent

  data class Feeds(
    val feeds: List<LanWebFeedItem>,
  ) : LanWebContent
}

internal object LanWebViews {
  const val UNREAD = "unread"
  const val SAVED = "saved"
  const val READ_LATER = "read-later"
  const val FEEDS = "feeds"

}
