package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.YomitoriApplication

@Composable
fun TaskQueueScreen(onDismiss: () -> Unit) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  AiTaskQueueScreen(
    repository = application.container.aiTaskQueueRepository,
    onDismiss = onDismiss,
  )
}
