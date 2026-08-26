package dev.terashima.yomitorirss.feature.integrated.ui

import android.net.Uri
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import dev.terashima.yomitorirss.feature.reddit.RedditSubscriptionKind
import dev.terashima.yomitorirss.feature.reddit.RedditUiState

internal fun integratedItemActions(
  target: IntegratedTarget,
  redditState: RedditUiState,
  onOpenArticle: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onSubscribeRedditThread: (Article) -> Unit,
  onUnsubscribeRedditThread: (Article) -> Unit,
): List<IntegratedItemAction> = when (target) {
  is IntegratedTarget.Rss -> listOf(
    IntegratedItemAction("はてなブックマークコメントを見る") {
      onOpenArticle(target.article.withHatenaBookmarkCommentsUrl())
    },
  )

  is IntegratedTarget.Reddit -> buildList {
    add(
      IntegratedItemAction("はてなブックマークコメントを見る") {
        onOpenArticle(target.article.withHatenaBookmarkCommentsUrl())
      },
    )
    add(
      IntegratedItemAction("要約") {
        onSummarize(target.article)
      },
    )
    val threadId = RedditSourceBoundary.threadId(target.article.url)
    if (threadId != null) {
      val subscribed = redditState.subscriptions.any { subscription ->
        subscription.kind == RedditSubscriptionKind.THREAD &&
          RedditSourceBoundary.threadId(subscription.feedUrl) == threadId
      }
      add(
        if (subscribed) {
          IntegratedItemAction("スレッドの購読を解除") {
            onUnsubscribeRedditThread(target.article)
          }
        } else {
          IntegratedItemAction("スレッドを購読") {
            onSubscribeRedditThread(target.article)
          }
        },
      )
    }
  }

  is IntegratedTarget.YouTube,
  is IntegratedTarget.Mail -> emptyList()
}

private fun Article.withHatenaBookmarkCommentsUrl(): Article = copy(
  url = "https://b.hatena.ne.jp/entry?url=${Uri.encode(url)}",
)
