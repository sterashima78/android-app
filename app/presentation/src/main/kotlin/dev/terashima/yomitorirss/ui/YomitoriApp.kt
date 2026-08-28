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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.terashima.yomitorirss.AppRouteDependencies
import dev.terashima.yomitorirss.feature.bookmark.rememberBookmarkEditController
import dev.terashima.yomitorirss.feature.game.GAME_ROUTE
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
import dev.terashima.yomitorirss.feature.reddit.rememberRedditRouteController
import dev.terashima.yomitorirss.feature.rss.rememberRssRouteController
import dev.terashima.yomitorirss.feature.x.X_ROUTE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun YomitoriApp(
  navController: NavHostController,
  routeDependencies: AppRouteDependencies,
  navigationRequests: Flow<AppNavigationTarget>,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onOpenArticleUrl: (String) -> Unit,
  onOpenWebContent: (String) -> Boolean,
  onOpenWebServer: () -> Unit,
  onExitApp: () -> Unit,
) {
  val currentBackStackEntry by navController.currentBackStackEntryAsState()
  val selectedRoute = currentBackStackEntry?.destination?.route ?: INTEGRATED_ROUTE
  val selectedSection = selectedRoute.appSection()
  val snackbarHostState = remember { SnackbarHostState() }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val rssController = rememberRssRouteController()
  val redditController = rememberRedditRouteController()
  val bookmarkEditController = rememberBookmarkEditController()
  val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
  var gameFullscreen by rememberSaveable { mutableStateOf(false) }
  val hideAppChrome = shouldHideAppChrome(selectedRoute, gameFullscreen)

  LaunchedEffect(navController, navigationRequests) {
    navigationRequests.collect { target -> navController.navigateTopLevel(target.appRoute()) }
  }

  currentBackStackEntry?.let { owner ->
    FeatureMessageEffects(
      selectedRoute = selectedRoute,
      viewModelStoreOwner = owner,
      snackbarHostState = snackbarHostState,
      routeDependencies = routeDependencies,
    )
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = drawerState.isOpen,
    drawerContent = {
      AppDrawerContent(
        selectedSection = selectedSection,
        onSelectSection = { section ->
          navController.navigateTopLevel(section.defaultRoute())
          scope.launch { drawerState.close() }
        },
      )
    },
  ) {
    BackHandler {
      when (
        rootBackAction(
          isDrawerOpen = drawerState.isOpen,
          canNavigateBack = navController.previousBackStackEntry != null,
        )
      ) {
        RootBackAction.POP_NAVIGATION -> navController.popBackStack()
        RootBackAction.OPEN_DRAWER -> openDrawer()
        RootBackAction.EXIT_APP -> onExitApp()
      }
    }

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = if (selectedRoute == X_ROUTE) {
        WindowInsets(0, 0, 0, 0)
      } else {
        ScaffoldDefaults.contentWindowInsets
      },
      topBar = {
        if (!hideAppChrome) {
          currentBackStackEntry?.let { owner ->
            AppTopBarRoute(
              selectedRoute = selectedRoute,
              viewModelStoreOwner = owner,
              routeDependencies = routeDependencies,
              rssController = rssController,
              redditController = redditController,
              onOpenDrawer = openDrawer,
            )
          }
        }
      },
      bottomBar = {
        AppBottomBar(
          selectedRoute = selectedRoute,
          onSelectRoute = { route -> navController.navigateTopLevel(route) },
        )
      },
    ) { padding ->
      AppNavHost(
        navController = navController,
        modifier = Modifier.fillMaxSize().padding(padding),
        routeDependencies = routeDependencies,
        rssController = rssController,
        redditController = redditController,
        bookmarkEditController = bookmarkEditController,
        biometricLockEnabled = biometricLockEnabled,
        onBiometricLockEnabledChange = onBiometricLockEnabledChange,
        onOpenArticle = { article -> onOpenArticleUrl(article.url) },
        onOpenWebContent = onOpenWebContent,
        onOpenWebServer = onOpenWebServer,
        onGameFullscreenChange = { fullscreen -> gameFullscreen = fullscreen },
      )
    }
  }

  currentBackStackEntry?.let { owner ->
    if (selectedRoute.usesBookmarkEditOverlay()) {
      BookmarkEditOverlay(
        routeDependencies = routeDependencies,
        controller = bookmarkEditController,
        viewModelStoreOwner = owner,
      )
    }
    if (selectedRoute.usesSummaryOverlay()) {
      SummaryOverlay(
        routeDependencies = routeDependencies,
        viewModelStoreOwner = owner,
      )
    }
  }
}

internal fun shouldHideAppChrome(
  selectedRoute: String,
  gameFullscreen: Boolean,
): Boolean = selectedRoute == GAME_ROUTE && gameFullscreen
