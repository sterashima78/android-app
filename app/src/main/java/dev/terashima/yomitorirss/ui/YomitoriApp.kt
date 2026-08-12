@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.core.airuntime.SummaryProgress
import dev.terashima.yomitorirss.core.designsystem.PullToRefreshContainer
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.backup.GoogleDriveBackupDialog
import dev.terashima.yomitorirss.feature.bookmark.ArticleFolderDialog
import dev.terashima.yomitorirss.feature.bookmark.ArticleTagsDialog
import dev.terashima.yomitorirss.feature.bookmark.BookmarkScreen
import dev.terashima.yomitorirss.feature.bookmark.BookmarkTab
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.chat.AiChatScreen
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.integrated.IntegratedRoute
import dev.terashima.yomitorirss.feature.library.LibraryRoute
import dev.terashima.yomitorirss.feature.mail.MailScreen
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.navigation.AppSection
import dev.terashima.yomitorirss.feature.navigation.AppViewModel
import dev.terashima.yomitorirss.feature.navigation.MainTab
import dev.terashima.yomitorirss.feature.reddit.RedditScreen
import dev.terashima.yomitorirss.feature.reddit.RedditTab
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.AddFeedDialog
import dev.terashima.yomitorirss.feature.rss.CandidateDialog
import dev.terashima.yomitorirss.feature.rss.FeedScreen
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssScreen
import dev.terashima.yomitorirss.feature.rss.RssTab
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.settings.SettingsScreen
import dev.terashima.yomitorirss.feature.summary.SummaryDialog
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.task.TaskScreen
import dev.terashima.yomitorirss.feature.workout.WorkoutRoute
import dev.terashima.yomitorirss.feature.x.XViewerScreen
import dev.terashima.yomitorirss.feature.youtube.YouTubeRoute
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun YomitoriApp(
  appViewModel: AppViewModel,
  rssViewModel: RssViewModel,
  redditViewModel: RedditViewModel,
  feedViewModel: FeedViewModel,
  bookmarkViewModel: BookmarkViewModel,
  mailViewModel: MailViewModel,
  summaryViewModel: SummaryViewModel,
  backupViewModel: BackupViewModel,
  aiSettingsViewModel: AiSettingsViewModel,
  chatViewModel: ChatViewModel,
  onOpenArticle: (Article) -> Unit,
  onOpenWebServer: () -> Unit,
  onAddMailAccount: () -> Unit,
) {
  val appState by appViewModel.state.collectAsState()
  val rssState by rssViewModel.state.collectAsState()
  val redditState by redditViewModel.state.collectAsState()
  val feedState by feedViewModel.state.collectAsState()
  val bookmarkState by bookmarkViewModel.state.collectAsState()
  val mailState by mailViewModel.state.collectAsState()
  val summaryState by summaryViewModel.state.collectAsState()
  val backupState by backupViewModel.state.collectAsState()
  val chatState by chatViewModel.state.collectAsState()
  val aiState by aiSettingsViewModel.state.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var showAddFeed by remember { mutableStateOf(false) }
  var showModels by remember { mutableStateOf(false) }
  var showSummaryPrompt by remember { mutableStateOf(false) }
  var showBackup by remember { mutableStateOf(false) }
  var showMarkAllReadConfirmation by remember { mutableStateOf(false) }
  var editTagsFor by remember { mutableStateOf<Article?>(null) }
  var moveFolderFor by remember { mutableStateOf<Article?>(null) }

  val selectedTab = appState.selectedTab
  val selectedSection = selectedTab.appSection()
  val selectedRssTab = selectedTab.rssTab()
  val selectedRedditTab = selectedTab.redditTab()
  val selectedBookmarkTab = selectedTab.bookmarkTab()
  val selectedFeatureInitialized = when (selectedSection) {
    AppSection.HOME -> true
    AppSection.RSS -> if (selectedTab == MainTab.FEEDS) feedState.initialized else rssState.initialized
    AppSection.REDDIT -> redditState.initialized
    AppSection.BOOKMARKS -> bookmarkState.initialized
    AppSection.LIBRARY -> true
    AppSection.MAIL -> mailState.initialized
    AppSection.YOUTUBE -> true
    AppSection.X -> true
    AppSection.TASKS -> true
    AppSection.WORKOUT -> true
    AppSection.AI_CHAT -> chatState.initialized
    AppSection.SETTINGS -> true
  }

  val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json"),
  ) { uri -> uri?.toString()?.let(backupViewModel::exportBackup) }
  val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(backupViewModel::importBackup) }
  val bookmarkCsvImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(bookmarkViewModel::importCsv) }
  val bookmarkHtmlImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(bookmarkViewModel::importHtml) }
  val feedOpmlImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(feedViewModel::importOpml) }
  val backupFolderLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
  ) { uri -> uri?.toString()?.let(backupViewModel::configureGoogleDrive) }

  LaunchedEffect(appState.message) {
    val message = appState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    appViewModel.dismissMessage()
  }
  LaunchedEffect(rssState.message) {
    val message = rssState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    rssViewModel.dismissMessage()
  }
  LaunchedEffect(redditState.message) {
    val message = redditState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    redditViewModel.dismissMessage()
  }
  LaunchedEffect(feedState.message) {
    val message = feedState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    feedViewModel.dismissMessage()
  }
  LaunchedEffect(bookmarkState.message) {
    val message = bookmarkState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    bookmarkViewModel.dismissMessage()
  }
  LaunchedEffect(summaryState.message) {
    val message = summaryState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    summaryViewModel.dismissMessage()
  }
  LaunchedEffect(backupState.message) {
    val message = backupState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    backupViewModel.dismissMessage()
  }
  LaunchedEffect(aiState.message) {
    val message = aiState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    aiSettingsViewModel.dismissMessage()
  }
  LaunchedEffect(backupState.restoreCompleted) {
    if (backupState.restoreCompleted) {
      appViewModel.selectTab(MainTab.INTEGRATED)
      backupViewModel.consumeRestoreCompleted()
    }
  }
  LaunchedEffect(bookmarkState.importCompleted) {
    if (bookmarkState.importCompleted) {
      appViewModel.selectTab(MainTab.SAVED)
      bookmarkViewModel.consumeImportCompleted()
    }
  }
  LaunchedEffect(feedState.importCompleted) {
    if (feedState.importCompleted) {
      appViewModel.selectTab(MainTab.FEEDS)
      feedViewModel.consumeImportCompleted()
    }
  }
  LaunchedEffect(feedState.feedAdded) {
    if (feedState.feedAdded) {
      appViewModel.selectTab(MainTab.FEEDS)
      feedViewModel.consumeFeedAdded()
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = drawerState.isOpen,
    drawerContent = {
      ModalDrawerSheet {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 20.dp)) {
          Text(
            text = "Yomitori",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
          )
          Spacer(Modifier.height(20.dp))
          AppSection.entries.forEach { section ->
            NavigationDrawerItem(
              selected = selectedSection == section,
              onClick = {
                appViewModel.selectTab(section.defaultTab())
                scope.launch { drawerState.close() }
              },
              icon = {
                Icon(
                  imageVector = when (section) {
                    AppSection.HOME -> Icons.Default.Home
                    AppSection.RSS -> Icons.Default.RssFeed
                    AppSection.REDDIT -> Icons.Default.Forum
                    AppSection.BOOKMARKS -> Icons.Default.Bookmark
                    AppSection.LIBRARY -> Icons.Default.LibraryBooks
                    AppSection.MAIL -> Icons.Default.Email
                    AppSection.YOUTUBE -> Icons.Default.PlayArrow
                    AppSection.X -> Icons.Default.Public
                    AppSection.TASKS -> Icons.Default.Checklist
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
    },
  ) {
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = if (selectedTab == MainTab.X) {
        WindowInsets(0, 0, 0, 0)
      } else {
        ScaffoldDefaults.contentWindowInsets
      },
      topBar = {
        if (selectedTab.usesGlobalTopBar()) {
          TopAppBar(
            navigationIcon = {
              IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                IconButton(onClick = { showMarkAllReadConfirmation = true }) {
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
                IconButton(onClick = { showAddFeed = true }) {
                  Icon(Icons.Default.Add, contentDescription = "フィードを追加")
                }
              }
            },
          )
        }
      },
      bottomBar = {
        when (selectedSection) {
          AppSection.RSS -> NavigationBar {
            RssTab.entries.forEach { tab ->
              NavigationBarItem(
                selected = selectedRssTab == tab,
                onClick = { appViewModel.selectTab(tab.mainTab()) },
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
                selected = selectedRedditTab == tab,
                onClick = { appViewModel.selectTab(tab.mainTab()) },
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
                selected = selectedBookmarkTab == tab,
                onClick = { appViewModel.selectTab(tab.mainTab()) },
                icon = {
                  Icon(
                    imageVector = when (tab) {
                      BookmarkTab.BOOKMARKS -> Icons.Default.Bookmark
                      BookmarkTab.FOLDERS -> Icons.Default.Folder
                      BookmarkTab.TAGS -> Icons.Default.Label
                      BookmarkTab.HISTORY -> Icons.Default.History
                    },
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label, maxLines = 1) },
              )
            }
          }

          AppSection.HOME,
          AppSection.LIBRARY,
          AppSection.MAIL,
          AppSection.YOUTUBE,
          AppSection.X,
          AppSection.TASKS,
          AppSection.WORKOUT,
          AppSection.AI_CHAT,
          AppSection.SETTINGS -> Unit
        }
      },
    ) { padding ->
      if (!selectedFeatureInitialized) {
        Box(
          modifier = Modifier.fillMaxSize().padding(padding),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      } else {
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when (selectedTab) {
          MainTab.INTEGRATED -> IntegratedRoute(
            modifier = contentModifier,
            rssViewModel = rssViewModel,
            redditViewModel = redditViewModel,
            feedViewModel = feedViewModel,
            mailViewModel = mailViewModel,
            onOpenArticle = onOpenArticle,
            onOpenMail = { thread ->
              mailViewModel.openThread(thread)
              appViewModel.selectTab(MainTab.MAIL)
            },
          )

          MainTab.UNREAD,
          MainTab.READ_LATER -> PullToRefreshContainer(
            modifier = contentModifier,
            isRefreshing = feedState.refreshing,
            onRefresh = feedViewModel::refresh,
          ) {
            RssScreen(
              modifier = Modifier.fillMaxSize(),
              tab = requireNotNull(selectedRssTab),
              state = rssState,
              onMarkRead = rssViewModel::markRead,
              onSaveAndRead = rssViewModel::saveAndRead,
              onReadLater = rssViewModel::readLater,
              onUnsave = rssViewModel::unsave,
              onRemoveReadLater = rssViewModel::removeReadLater,
              onOpen = onOpenArticle,
              onSummarize = { summaryViewModel.summarize(it) },
              onEditTags = { editTagsFor = it },
              onMoveFolder = { moveFolderFor = it },
            )
          }

          MainTab.REDDIT_UNREAD,
          MainTab.REDDIT_READ_LATER,
          MainTab.REDDIT_SUBSCRIPTIONS -> PullToRefreshContainer(
            modifier = contentModifier,
            isRefreshing = redditState.refreshing,
            onRefresh = redditViewModel::refresh,
          ) {
            RedditScreen(
              modifier = Modifier.fillMaxSize(),
              tab = requireNotNull(selectedRedditTab),
              state = redditState,
              onMarkRead = redditViewModel::markRead,
              onSaveAndRead = redditViewModel::saveAndRead,
              onReadLater = redditViewModel::readLater,
              onUnsave = redditViewModel::unsave,
              onRemoveReadLater = redditViewModel::removeReadLater,
              onOpen = onOpenArticle,
              onSummarize = { summaryViewModel.summarize(it) },
              onSubscribeThread = redditViewModel::subscribeThread,
              onUnsubscribeThread = redditViewModel::unsubscribeThread,
              onAddCommunity = redditViewModel::addCommunity,
              onDeleteSubscription = redditViewModel::deleteSubscription,
            )
          }

          MainTab.SAVED,
          MainTab.FOLDERS,
          MainTab.TAGS,
          MainTab.HISTORY -> BookmarkScreen(
            modifier = contentModifier,
            tab = requireNotNull(selectedBookmarkTab),
            state = bookmarkState,
            onTagSelected = bookmarkViewModel::selectTag,
            onFolderSelected = bookmarkViewModel::selectFolder,
            onOpen = onOpenArticle,
            onSummarize = { summaryViewModel.summarize(it) },
            onEditTags = { editTagsFor = it },
            onMoveFolder = { moveFolderFor = it },
            onUnsave = bookmarkViewModel::unsave,
            onMarkUnread = bookmarkViewModel::markUnread,
            onCreateFolder = bookmarkViewModel::createFolder,
            onRenameFolder = bookmarkViewModel::renameFolder,
            onDeleteFolder = bookmarkViewModel::deleteFolder,
            onCreateTag = bookmarkViewModel::createTag,
            onRenameTag = bookmarkViewModel::renameTag,
            onDeleteTag = bookmarkViewModel::deleteTag,
          )

          MainTab.LIBRARY -> LibraryRoute(modifier = contentModifier)

          MainTab.MAIL -> MailScreen(
            modifier = contentModifier,
            state = mailState,
            onAddAccount = onAddMailAccount,
            onRemoveSelectedAccount = mailViewModel::removeSelectedAccount,
            onSelectAccount = mailViewModel::selectAccount,
            onSelectMailbox = mailViewModel::selectMailbox,
            onUpdateQuery = mailViewModel::updateQuery,
            onSearch = mailViewModel::search,
            onRefresh = mailViewModel::refresh,
            onOpenThread = mailViewModel::openThread,
            onCloseThread = mailViewModel::closeThread,
            onToggleRead = mailViewModel::toggleRead,
            onToggleStarred = mailViewModel::toggleStarred,
            onToggleReadLater = mailViewModel::toggleReadLater,
            onArchive = mailViewModel::archive,
            onTrash = mailViewModel::trash,
            onApplyLabel = mailViewModel::applyLabel,
            onDismissMessage = mailViewModel::dismissMessage,
          )

          MainTab.YOUTUBE -> YouTubeRoute(modifier = contentModifier)

          MainTab.X -> Box(modifier = contentModifier) {
            XViewerScreen(modifier = Modifier.fillMaxSize())
            Surface(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                  WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End),
                )
                .padding(8.dp),
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
              tonalElevation = 4.dp,
            ) {
              IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, contentDescription = "メニュー")
              }
            }
          }

          MainTab.TASKS -> TaskScreen(modifier = contentModifier)

          MainTab.WORKOUT -> WorkoutRoute(modifier = contentModifier)

          MainTab.AI_CHAT -> AiChatScreen(
            modifier = contentModifier,
            state = chatState,
            onSelectSession = chatViewModel::selectSession,
            onStartNewSession = chatViewModel::startNewSession,
            onSendMessage = chatViewModel::sendMessage,
          )

          MainTab.FEEDS -> PullToRefreshContainer(
            modifier = contentModifier,
            isRefreshing = feedState.refreshing,
            onRefresh = feedViewModel::refresh,
          ) {
            FeedScreen(
              modifier = Modifier.fillMaxSize(),
              feeds = feedState.feeds,
              folders = feedState.folders,
              onAdd = { showAddFeed = true },
              onDelete = feedViewModel::deleteFeed,
              onCreateFolder = feedViewModel::createFolder,
              onRenameFolder = feedViewModel::renameFolder,
              onDeleteFolder = feedViewModel::deleteFolder,
              onMoveFeed = feedViewModel::moveFeedToFolder,
            )
          }

          MainTab.SETTINGS -> SettingsScreen(
            modifier = contentModifier,
            tagCount = bookmarkState.tags.size,
            onImportBookmarkCsv = {
              bookmarkCsvImportLauncher.launch(
                arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain"),
              )
            },
            onImportBookmarkHtml = {
              bookmarkHtmlImportLauncher.launch(
                arrayOf("text/html", "application/xhtml+xml", "text/plain"),
              )
            },
            onOpenModels = { showModels = true },
            onOpenSummaryPrompt = { showSummaryPrompt = true },
            onOpenDriveBackup = {
              backupViewModel.refreshStatus()
              showBackup = true
            },
            onExportBackup = {
              exportLauncher.launch("rss-reader-backup-${LocalDate.now()}.json")
            },
            onImportBackup = {
              importLauncher.launch(arrayOf("application/json", "text/json"))
            },
            onOpenWebServer = onOpenWebServer,
          )
        }
      }
    }
  }

  if (showMarkAllReadConfirmation) {
    AlertDialog(
      onDismissRequest = { showMarkAllReadConfirmation = false },
      title = { Text("すべて既読にしますか？") },
      text = { Text("この画面の未読をすべて既読にします。") },
      confirmButton = {
        TextButton(
          onClick = {
            showMarkAllReadConfirmation = false
            if (selectedSection == AppSection.REDDIT) {
              redditViewModel.markAllUnreadAsRead()
            } else {
              rssViewModel.markAllUnreadAsRead()
            }
          },
        ) {
          Text("すべて既読")
        }
      },
      dismissButton = {
        TextButton(onClick = { showMarkAllReadConfirmation = false }) {
          Text("キャンセル")
        }
      },
    )
  }
  if (showAddFeed) {
    AddFeedDialog(
      onDismiss = { showAddFeed = false },
      onAdd = {
        showAddFeed = false
        feedViewModel.inspectAndAddFeed(it)
      },
    )
  }
  if (feedState.feedCandidates.isNotEmpty()) {
    CandidateDialog(
      candidates = feedState.feedCandidates,
      onDismiss = feedViewModel::dismissFeedCandidates,
      onSelect = feedViewModel::addFeedCandidate,
    )
  }
  if (showModels) {
    ModelManagerDialog(
      supported = aiState.supported,
      models = aiState.models,
      inferenceBackend = aiState.inferenceBackend,
      thinkingEnabled = aiState.thinkingEnabled,
      progressModelId = aiState.downloadProgress?.modelId,
      progressText = aiState.downloadProgress?.let {
        val percent = if (it.totalBytes > 0) it.downloadedBytes * 100 / it.totalBytes else 0
        "${it.phase} $percent%"
      },
      onDismiss = { showModels = false },
      onBackendChange = aiSettingsViewModel::setInferenceBackend,
      onThinkingChange = aiSettingsViewModel::setThinkingEnabled,
      onDownload = aiSettingsViewModel::downloadModel,
      onSelect = aiSettingsViewModel::selectModel,
      onDelete = aiSettingsViewModel::deleteModel,
    )
  }
  if (showSummaryPrompt) {
    SummaryPromptDialog(
      prompt = aiState.summaryPrompt,
      onDismiss = { showSummaryPrompt = false },
      onSave = {
        showSummaryPrompt = false
        aiSettingsViewModel.updateSummaryPrompt(it)
      },
      onReset = {
        showSummaryPrompt = false
        aiSettingsViewModel.resetSummaryPrompt()
      },
    )
  }
  if (showBackup) {
    GoogleDriveBackupDialog(
      state = backupState,
      onDismiss = { showBackup = false },
      onSelectFolder = {
        val initialUri = backupState.folderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        backupFolderLauncher.launch(initialUri)
      },
      onBackupNow = backupViewModel::backupToGoogleDriveNow,
      onDisable = backupViewModel::disableGoogleDrive,
    )
  }
  editTagsFor?.let { article ->
    ArticleTagsDialog(
      article = article,
      bookmarkDetails = bookmarkState.bookmarkDetails[article.id],
      tags = bookmarkState.tags,
      onDismiss = { editTagsFor = null },
      onSave = {
        editTagsFor = null
        bookmarkViewModel.replaceArticleTags(article, it)
      },
    )
  }
  moveFolderFor?.let { article ->
    ArticleFolderDialog(
      article = article,
      bookmarkDetails = bookmarkState.bookmarkDetails[article.id],
      folders = bookmarkState.folders,
      onDismiss = { moveFolderFor = null },
      onSave = { folderId ->
        moveFolderFor = null
        bookmarkViewModel.moveArticleToFolder(article, folderId)
      },
    )
  }
  summaryState.article?.let { article ->
    SummaryDialog(
      article = article,
      text = summaryState.text,
      loading = summaryState.loading,
      progress = aiState.summaryProgress?.let(::summaryProgressLabel),
      onDismiss = summaryViewModel::dismissSummary,
      onRetry = { summaryViewModel.summarize(article, forceRefresh = true) },
    )
  }
}

internal fun MainTab.usesGlobalTopBar(): Boolean = this != MainTab.X

private fun MainTab.appSection(): AppSection = when (this) {
  MainTab.INTEGRATED -> AppSection.HOME

  MainTab.UNREAD,
  MainTab.READ_LATER,
  MainTab.FEEDS -> AppSection.RSS

  MainTab.REDDIT_UNREAD,
  MainTab.REDDIT_READ_LATER,
  MainTab.REDDIT_SUBSCRIPTIONS -> AppSection.REDDIT

  MainTab.SAVED,
  MainTab.FOLDERS,
  MainTab.TAGS,
  MainTab.HISTORY -> AppSection.BOOKMARKS

  MainTab.LIBRARY -> AppSection.LIBRARY
  MainTab.MAIL -> AppSection.MAIL
  MainTab.YOUTUBE -> AppSection.YOUTUBE
  MainTab.X -> AppSection.X
  MainTab.TASKS -> AppSection.TASKS
  MainTab.WORKOUT -> AppSection.WORKOUT
  MainTab.AI_CHAT -> AppSection.AI_CHAT
  MainTab.SETTINGS -> AppSection.SETTINGS
}

private fun MainTab.rssTab(): RssTab? = when (this) {
  MainTab.UNREAD -> RssTab.UNREAD
  MainTab.READ_LATER -> RssTab.READ_LATER
  MainTab.FEEDS -> RssTab.FEEDS
  else -> null
}

private fun MainTab.redditTab(): RedditTab? = when (this) {
  MainTab.REDDIT_UNREAD -> RedditTab.UNREAD
  MainTab.REDDIT_READ_LATER -> RedditTab.READ_LATER
  MainTab.REDDIT_SUBSCRIPTIONS -> RedditTab.SUBSCRIPTIONS
  else -> null
}

private fun MainTab.bookmarkTab(): BookmarkTab? = when (this) {
  MainTab.SAVED -> BookmarkTab.BOOKMARKS
  MainTab.FOLDERS -> BookmarkTab.FOLDERS
  MainTab.TAGS -> BookmarkTab.TAGS
  MainTab.HISTORY -> BookmarkTab.HISTORY
  else -> null
}

private fun MainTab.screenTitle(): String = when (this) {
  MainTab.INTEGRATED -> "統合ビュー"
  MainTab.UNREAD -> "RSS・未読"
  MainTab.READ_LATER -> "RSS・あとで読む"
  MainTab.REDDIT_UNREAD -> "Reddit・未読"
  MainTab.REDDIT_READ_LATER -> "Reddit・あとで読む"
  MainTab.REDDIT_SUBSCRIPTIONS -> "Reddit・購読管理"
  MainTab.SAVED -> "ブックマーク・一覧"
  MainTab.FOLDERS -> "ブックマーク・フォルダ"
  MainTab.TAGS -> "ブックマーク・タグ"
  MainTab.HISTORY -> "ブックマーク・履歴"
  MainTab.LIBRARY -> "蔵書"
  MainTab.MAIL -> "メール"
  MainTab.YOUTUBE -> "YouTube"
  MainTab.X -> "X"
  MainTab.TASKS -> "タスク"
  MainTab.WORKOUT -> "ワークアウト"
  MainTab.AI_CHAT -> "AIチャット"
  MainTab.FEEDS -> "RSS・フィード管理"
  MainTab.SETTINGS -> "設定"
}

private fun AppSection.defaultTab(): MainTab = when (this) {
  AppSection.HOME -> MainTab.INTEGRATED
  AppSection.RSS -> MainTab.UNREAD
  AppSection.REDDIT -> MainTab.REDDIT_UNREAD
  AppSection.BOOKMARKS -> MainTab.SAVED
  AppSection.LIBRARY -> MainTab.LIBRARY
  AppSection.MAIL -> MainTab.MAIL
  AppSection.YOUTUBE -> MainTab.YOUTUBE
  AppSection.X -> MainTab.X
  AppSection.TASKS -> MainTab.TASKS
  AppSection.WORKOUT -> MainTab.WORKOUT
  AppSection.AI_CHAT -> MainTab.AI_CHAT
  AppSection.SETTINGS -> MainTab.SETTINGS
}

private fun RssTab.mainTab(): MainTab = when (this) {
  RssTab.UNREAD -> MainTab.UNREAD
  RssTab.READ_LATER -> MainTab.READ_LATER
  RssTab.FEEDS -> MainTab.FEEDS
}

private fun RedditTab.mainTab(): MainTab = when (this) {
  RedditTab.UNREAD -> MainTab.REDDIT_UNREAD
  RedditTab.READ_LATER -> MainTab.REDDIT_READ_LATER
  RedditTab.SUBSCRIPTIONS -> MainTab.REDDIT_SUBSCRIPTIONS
}

private fun BookmarkTab.mainTab(): MainTab = when (this) {
  BookmarkTab.BOOKMARKS -> MainTab.SAVED
  BookmarkTab.FOLDERS -> MainTab.FOLDERS
  BookmarkTab.TAGS -> MainTab.TAGS
  BookmarkTab.HISTORY -> MainTab.HISTORY
}

private fun summaryProgressLabel(progress: SummaryProgress): String = when (progress.stage) {
  "preparing_model" -> "${progress.modelName ?: "モデル"} を準備しています"
  "generating_summary" -> "${progress.modelName ?: "モデル"} で要約を生成しています"
  else -> progress.modelName?.let { "${progress.stage}: $it" } ?: progress.stage
}