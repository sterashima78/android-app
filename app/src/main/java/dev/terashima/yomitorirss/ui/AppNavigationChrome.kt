@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.bookmark.BookmarkTab
import dev.terashima.yomitorirss.feature.navigation.AppSection
import dev.terashima.yomitorirss.feature.navigation.MainTab
import dev.terashima.yomitorirss.feature.reddit.RedditRouteController
import dev.terashima.yomitorirss.feature.reddit.RedditTab
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssRouteController
import dev.terashima.yomitorirss.feature.rss.RssTab
import dev.terashima.yomitorirss.feature.rss.RssViewModel

@Composable
internal fun AppDrawerContent(
  selectedSection: AppSection,
  onSelectSection: (AppSection) -> Unit,
) {
  ModalDrawerSheet {
    Column(
      Modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 20.dp),
    ) {
      Text(
        text = "Mosaic",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      Spacer(Modifier.height(20.dp))
      AppSection.entries.forEach { section ->
        NavigationDrawerItem(
          selected = selectedSection == section,
          onClick = { onSelectSection(section) },
          icon = {
            Icon(
              imageVector = when (section) {
                AppSection.HOME -> Icons.Default.Home
                AppSection.RSS -> Icons.Default.RssFeed
                AppSection.REDDIT -> Icons.Default.Forum
                AppSection.BOOKMARKS -> Icons.Default.Bookmark
                AppSection.LIBRARY -> Icons.Default.LibraryBooks
                AppSection.KNOWLEDGE -> Icons.Default.MenuBook
                AppSection.ASSETS -> Icons.Default.AccountBalanceWallet
                AppSection.MAIL -> Icons.Default.Email
                AppSection.YOUTUBE -> Icons.Default.PlayArrow
                AppSection.X -> Icons.Default.Public
                AppSection.TASKS -> Icons.Default.Checklist
                AppSection.CALENDAR -> Icons.Default.CalendarMonth
                AppSection.GAME -> Icons.Default.SportsEsports
                AppSection.HEALTH -> Icons.Default.Favorite
                AppSection.WORKOUT -> Icons.Default.FitnessCenter
                AppSection.AI_CHAT -> Icons.Default.Chat
                AppSection.SETTINGS -> Icons.Default.Settings
              },
              contentDescription = null,
            )
          },
          label = { Text(section.label) },
        )
      }
    }
  }
}

@Composable
internal fun AppTopBar(
  selectedTab: MainTab,
  rssViewModel: RssViewModel,
  redditViewModel: RedditViewModel,
  feedViewModel: FeedViewModel,
  rssController: RssRouteController,
  redditController: RedditRouteController,
  onOpenDrawer: () -> Unit,
) {
  if (!selectedTab.usesGlobalTopBar()) return

  val rssState by rssViewModel.state.collectAsState()
  val redditState by redditViewModel.state.collectAsState()
  val feedState by feedViewModel.state.collectAsState()
  val selectedSection = selectedTab.appSection()
  val feedOpmlImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(feedViewModel::importOpml) }

  TopAppBar(
    navigationIcon = {
      IconButton(onClick = onOpenDrawer) {
        Icon(Icons.Default.Menu, contentDescription = "メニュー")
      }
    },
    title = {
      Column {
        Text(selectedTab.screenTitle())
        when (selectedSection) {
          AppSection.RSS -> feedState.refreshProgress
          AppSection.REDDIT -> redditState.refreshProgress
          else -> null
        }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    actions = {
      val hasUnread = when (selectedTab) {
        MainTab.UNREAD -> rssState.unread.isNotEmpty()
        MainTab.REDDIT_UNREAD -> redditState.unread.isNotEmpty()
        else -> false
      }
      if (hasUnread) {
        IconButton(
          onClick = {
            if (selectedTab == MainTab.REDDIT_UNREAD) {
              redditController.requestMarkAllRead()
            } else {
              rssController.requestMarkAllRead()
            }
          },
        ) {
          Icon(Icons.Default.DoneAll, contentDescription = "すべて既読")
        }
      }
      if (selectedTab == MainTab.FEEDS) {
        IconButton(
          onClick = {
            feedOpmlImportLauncher.launch(
              arrayOf(
                "application/xml",
                "text/xml",
                "text/x-opml",
                "application/x-opml",
                "application/octet-stream",
                "text/plain",
              ),
            )
          },
        ) {
          Icon(Icons.Default.UploadFile, contentDescription = "OPMLからインポート")
        }
        IconButton(onClick = rssController::requestAddFeed) {
          Icon(Icons.Default.Add, contentDescription = "フィードを追加")
        }
      }
    },
  )
}

@Composable
internal fun AppBottomBar(
  selectedTab: MainTab,
  onSelectTab: (MainTab) -> Unit,
) {
  when (selectedTab.appSection()) {
    AppSection.RSS -> NavigationBar {
      RssTab.entries.forEach { tab ->
        NavigationBarItem(
          selected = selectedTab.rssTab() == tab,
          onClick = { onSelectTab(tab.mainTab()) },
          icon = {
            Icon(
              imageVector = when (tab) {
                RssTab.UNREAD -> Icons.Default.RssFeed
                RssTab.READ_LATER -> Icons.Default.AccessTime
                RssTab.FEEDS -> Icons.Default.Folder
              },
              contentDescription = tab.label,
            )
          },
          label = { Text(tab.label, maxLines = 1) },
        )
      }
    }

    AppSection.REDDIT -> NavigationBar {
      RedditTab.entries.forEach { tab ->
        NavigationBarItem(
          selected = selectedTab.redditTab() == tab,
          onClick = { onSelectTab(tab.mainTab()) },
          icon = {
            Icon(
              imageVector = when (tab) {
                RedditTab.UNREAD -> Icons.Default.Forum
                RedditTab.READ_LATER -> Icons.Default.AccessTime
                RedditTab.SUBSCRIPTIONS -> Icons.Default.Checklist
              },
              contentDescription = tab.label,
            )
          },
          label = { Text(tab.label, maxLines = 1) },
        )
      }
    }

    AppSection.BOOKMARKS -> NavigationBar {
      BookmarkTab.entries.forEach { tab ->
        NavigationBarItem(
          selected = selectedTab.bookmarkTab() == tab,
          onClick = { onSelectTab(tab.mainTab()) },
          icon = {
            Icon(
              imageVector = when (tab) {
                BookmarkTab.BOOKMARKS -> Icons.Default.Bookmark
                BookmarkTab.FOLDERS -> Icons.Default.Folder
                BookmarkTab.TAGS -> Icons.Default.Label
                BookmarkTab.IMPORT -> Icons.Default.UploadFile
              },
              contentDescription = tab.label,
            )
          },
          label = { Text(tab.label, maxLines = 1) },
        )
      }
    }

    else -> Unit
  }
}
