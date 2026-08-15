package dev.terashima.yomitorirss.feature.youtube

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.YomitoriApplication

@Composable
fun YouTubeRoute(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val application = context.applicationContext as YomitoriApplication
  val viewModel: YouTubeViewModel = viewModel(
    factory = remember(application) {
      YouTubeViewModel.Factory(
        repository = application.container.youtubeRepository,
        bookmarkRepository = application.container.bookmarkRepository,
        backupChangeScheduler = application.container.backupChangeScheduler,
      )
    },
  )
  val state by viewModel.state.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.message) {
    val message = state.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    viewModel.dismissMessage()
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (!state.initialized) {
      CircularProgressIndicator(Modifier.align(Alignment.Center))
    } else {
      YouTubeScreen(
        modifier = Modifier.fillMaxSize(),
        state = state,
        onSelectTab = viewModel::selectTab,
        onRefresh = viewModel::refresh,
        onSubscribe = viewModel::subscribe,
        onUnsubscribe = viewModel::unsubscribe,
        onMarkRead = viewModel::markRead,
        onSaveAndRead = viewModel::saveAndRead,
        onUnsave = viewModel::unsave,
        onToggleWatchLater = viewModel::toggleWatchLater,
        onMarkAllRead = viewModel::markAllRead,
        onOpen = { video ->
          runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
          }
        },
      )
    }
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}
