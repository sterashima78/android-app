package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueScreen

@Composable
fun TaskQueueScreen(onDismiss: () -> Unit) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  SummaryTaskQueueScreen(
    repository = application.container.summaryTaskQueueRepository,
    onDismiss = onDismiss,
  )
}
