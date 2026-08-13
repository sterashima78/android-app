package dev.terashima.yomitorirss.feature.integrated

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.mail.Mailbox
import dev.terashima.yomitorirss.feature.reddit.RedditSubscriptionKind
import dev.terashima.yomitorirss.feature.reddit.RedditUiState
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.reddit.redditThreadId
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel

@Composable
fun IntegratedRoute(
  rssViewModel: RssViewModel,
  redditViewModel: RedditViewModel,
  feedViewModel: FeedViewModel,
  mailViewModel: MailViewModel,
  onOpenArticle: (Article) -> Unit,
  onOpenMail: (MailThread) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val application = context.applicationContext as YomitoriApplication
  val youtubeViewModel: YouTubeViewModel = viewModel(
    factory = remember(application) {
      YouTubeViewModel.Factory(
        repository = application.container.youtubeRepository,
        bookmarkRepository = application.container.bookmarkRepository,
      )
    },
  )
  val rssState by rssViewModel.state.collectAsState()
  val redditState by redditViewModel.state.collectAsState()
  val feedState by feedViewModel.state.collectAsState()
  val mailState by mailViewModel.state.collectAsState()
  val youtubeState by youtubeViewModel.state.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTabName by rememberSaveable { mutableStateOf(IntegratedTab.UNREAD.name) }
  val selectedTab = IntegratedTab.entries.firstOrNull { it.name == selectedTabName }
    ?: IntegratedTab.UNREAD

  LaunchedEffect(Unit) {
    mailViewModel.updateQuery("")
    mailViewModel.selectAccount(null)
  }
  LaunchedEffect(selectedTab) {
    mailViewModel.selectMailbox(
      when (selectedTab) {
        IntegratedTab.UNREAD -> Mailbox.UNREAD
        IntegratedTab.READ_LATER -> Mailbox.READ_LATER
      },
    )
  }
  LaunchedEffect(mailState.message) {
    val message = mailState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    mailViewModel.dismissMessage()
  }
  LaunchedEffect(youtubeState.message) {
    val message = youtubeState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    youtubeViewModel.dismissMessage()
  }

  val initialized = rssState.initialized &&
    redditState.initialized &&
    mailState.initialized &&
    youtubeState.initialized
  Box(modifier = modifier.fillMaxSize()) {
    if (!initialized) {
      CircularProgressIndicator(Modifier.align(Alignment.Center))
    } else {
      IntegratedScreen(
        modifier = Modifier.fillMaxSize(),
        selectedTab = selectedTab,
        rssState = rssState,
        redditState = redditState,
        youtubeState = youtubeState,
        mailState = mailState,
        isRefreshing = feedState.refreshing ||
          redditState.refreshing ||
          youtubeState.refreshing ||
          mailState.loading,
        onSelectTab = { selectedTabName = it.name },
        onRefresh = {
          feedViewModel.refresh()
          redditViewModel.refresh()
          youtubeViewModel.refresh()
          mailViewModel.refresh()
        },
        onMarkProcessed = { item ->
          when (item) {
            is IntegratedItem.Rss -> rssViewModel.markRead(item.article)
            is IntegratedItem.Reddit -> redditViewModel.markRead(item.article)
            is IntegratedItem.YouTube -> youtubeViewModel.markRead(item.video)
            is IntegratedItem.Mail -> mailViewModel.toggleRead(item.thread)
          }
        },
        onSave = { item ->
          when (item) {
            is IntegratedItem.Rss -> rssViewModel.saveAndRead(item.article)
            is IntegratedItem.Reddit -> redditViewModel.saveAndRead(item.article)
            is IntegratedItem.YouTube -> youtubeViewModel.saveAndRead(item.video)
            is IntegratedItem.Mail -> Unit
          }
        },
        onDefer = { item ->
          when (item) {
            is IntegratedItem.Rss -> rssViewModel.readLater(item.article)
            is IntegratedItem.Reddit -> redditViewModel.readLater(item.article)
            is IntegratedItem.YouTube -> youtubeViewModel.toggleWatchLater(item.video)
            is IntegratedItem.Mail -> mailViewModel.toggleReadLater(item.thread)
          }
        },
        onUnsave = { item ->
          when (item) {
            is IntegratedItem.Rss -> rssViewModel.unsave(item.article)
            is IntegratedItem.Reddit -> redditViewModel.unsave(item.article)
            is IntegratedItem.YouTube,
            is IntegratedItem.Mail -> Unit
          }
        },
        onRemoveDeferred = { item ->
          when (item) {
            is IntegratedItem.Rss -> rssViewModel.removeReadLater(item.article)
            is IntegratedItem.Reddit -> redditViewModel.removeReadLater(item.article)
            is IntegratedItem.YouTube -> youtubeViewModel.toggleWatchLater(item.video)
            is IntegratedItem.Mail -> mailViewModel.toggleReadLater(item.thread)
          }
        },
        onToggleMailStarred = { item -> mailViewModel.toggleStarred(item.thread) },
        onArchive = { item -> mailViewModel.archive(item.thread) },
        onOpen = { item ->
          when (item) {
            is IntegratedItem.Rss -> onOpenArticle(item.article)
            is IntegratedItem.Reddit -> onOpenArticle(item.article)
            is IntegratedItem.Mail -> onOpenMail(item.thread)
            is IntegratedItem.YouTube -> runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.video.url)))
            }
          }
        },
        actionsForItem = { item ->
          integratedItemActions(
            item = item,
            redditState = redditState,
            onOpenArticle = onOpenArticle,
            onSubscribeRedditThread = redditViewModel::subscribeThread,
            onUnsubscribeRedditThread = redditViewModel::unsubscribeThread,
          )
        },
      )
    }
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

internal fun integratedItemActions(
  item: IntegratedItem,
  redditState: RedditUiState,
  onOpenArticle: (Article) -> Unit,
  onSubscribeRedditThread: (Article) -> Unit,
  onUnsubscribeRedditThread: (Article) -> Unit,
): List<IntegratedItemAction> = when (item) {
  is IntegratedItem.Rss -> listOf(
    IntegratedItemAction("はてなブックマークコメントを見る") {
      onOpenArticle(item.article.withHatenaBookmarkCommentsUrl())
    },
  )

  is IntegratedItem.Reddit -> buildList {
    add(
      IntegratedItemAction("はてなブックマークコメントを見る") {
        onOpenArticle(item.article.withHatenaBookmarkCommentsUrl())
      },
    )
    val threadId = redditThreadId(item.article.url)
    if (threadId != null) {
      val subscribed = redditState.subscriptions.any { subscription ->
        subscription.kind == RedditSubscriptionKind.THREAD &&
          redditThreadId(subscription.feedUrl) == threadId
      }
      add(
        if (subscribed) {
          IntegratedItemAction("スレッドの購読を解除") {
            onUnsubscribeRedditThread(item.article)
          }
        } else {
          IntegratedItemAction("スレッドを購読") {
            onSubscribeRedditThread(item.article)
          }
        },
      )
    }
  }

  is IntegratedItem.YouTube,
  is IntegratedItem.Mail -> emptyList()
}

private fun Article.withHatenaBookmarkCommentsUrl(): Article = copy(
  url = "https://b.hatena.ne.jp/entry?url=${Uri.encode(url)}",
)
