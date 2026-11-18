package dev.terashima.yomitorirss.feature.integrated.ui

import android.net.Uri
import dev.terashima.yomitorirss.feature.article.Article

internal fun integratedItemActions(
  target: IntegratedTarget,
  onOpenArticle: (Article) -> Unit,
): List<IntegratedItemAction> = when (target) {
  is IntegratedTarget.Rss -> listOf(
    IntegratedItemAction("はてなブックマークコメントを見る") {
      onOpenArticle(target.article.withHatenaBookmarkCommentsUrl())
    },
  )
  is IntegratedTarget.Mail -> emptyList()
}

private fun Article.withHatenaBookmarkCommentsUrl(): Article = copy(
  url = "https://b.hatena.ne.jp/entry?url=${Uri.encode(url)}",
)