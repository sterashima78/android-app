package dev.terashima.yomitorirss.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.rememberBookmarkEditController
import dev.terashima.yomitorirss.feature.reddit.rememberRedditRouteController
import dev.terashima.yomitorirss.feature.rss.rememberRssRouteController
import kotlinx.coroutines.launch

@Composable
fun YomitoriApp(
  appViewModel: AppViewModel,
  routeDependencies: AppRouteDependencies,
  onOpenArticle: (Article) -> Unit,
  onOpenWebServer: () -> Unit,
  onExitApp: () -> Unit,
) {
  val appState by appViewModel.state.collectAsState()
  val selectedTab = appState.selectedTab
  val selectedSection = selectedTab.appSection()
  val snackbarHostState = remember { SnackbarHostState() }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val rssController = rememberRssRouteController()
  val redditController = rememberRedditRouteController()
  val bookmarkEditController = rememberBookmarkEditController()
  val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
  var gameFullscreen by rememberSaveable { mutableStateOf(false) }
  val isFullscreenGame = selectedTab == MainTab.GAME && gameFullscreen

  FeatureMessageEffects(
    selectedTab = selectedTab,
    snackbarHostState = snackbarHostState,
    appViewModel = appViewModel,
    routeDependencies = routeDependencies,
  )

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = drawerState.isOpen,
    drawerContent = {
      AppDrawerContent(
        selectedSection = selectedSection,
        onSelectSection = { section ->
          appViewModel.selectTab(section.defaultTab())
          scope.launch { drawerState.close() }
        },
      )
    },
  ) {
    BackHandler {
      when (rootBackAction(drawerState.isOpen)) {
        RootBackAction.OPEN_DRAWER -> openDrawer()
        RootBackAction.EXIT_APP -> onExitApp()
      }
    }

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = if (selectedTab == MainTab.X) {
        WindowInsets(0, 0, 0, 0)
      } else {
        ScaffoldDefaults.contentWindowInsets
      },
      topBar = {
        if (!isFullscreenGame) {
          AppTopBarRoute(
            selectedTab = selectedTab,
            routeDependencies = routeDependencies,
            rssController = rssController,
            redditController = redditController,
            onOpenDrawer = openDrawer,
          )
        }
      },
      bottomBar = {
        AppBottomBar(
          selectedTab = selectedTab,
          onSelectTab = appViewModel::selectTab,
        )
      },
    ) { padding ->
      CompositionLocalProvider(
        LocalGameFullscreenChange provides { fullscreen -> gameFullscreen = fullscreen },
      ) {
        AppFeatureContent(
          selectedTab = selectedTab,
          modifier = Modifier.fillMaxSize().padding(padding),
          appViewModel = appViewModel,
          routeDependencies = routeDependencies,
          rssController = rssController,
          redditController = redditController,
          bookmarkEditController = bookmarkEditController,
          onOpenArticle = onOpenArticle,
          onOpenWebServer = onOpenWebServer,
        )
      }
    }
  }

  if (selectedTab.usesBookmarkEditOverlay()) {
    BookmarkEditOverlay(
      routeDependencies = routeDependencies,
      controller = bookmarkEditController,
    )
  }
  if (selectedTab.usesSummaryOverlay()) {
    SummaryOverlay(routeDependencies = routeDependencies)
  }
}
