package dev.terashima.yomitorirss.feature.aitaskqueue

import androidx.compose.runtime.Composable

@Composable
fun AiTaskQueueRoute(
  repository: AiTaskQueueRepository,
  onDismiss: () -> Unit,
) {
  AiTaskQueueScreen(repository = repository, onDismiss = onDismiss)
}
