package dev.terashima.yomitorirss.feature.aitaskqueue

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.YomitoriApplication

@Composable
internal fun AiTaskQueueRoute(onDismiss: () -> Unit) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  AiTaskQueueScreen(
    repository = application.container.aiTaskQueueRepository,
    onDismiss = onDismiss,
  )
}
