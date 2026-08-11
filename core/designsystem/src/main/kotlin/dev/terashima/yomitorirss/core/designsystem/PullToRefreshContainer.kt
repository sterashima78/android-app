package dev.terashima.yomitorirss.core.designsystem

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

@Composable
fun PullToRefreshContainer(
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val canRefresh = enabled && !isRefreshing
  PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = { if (canRefresh) onRefresh() },
    modifier = modifier.semantics {
      customActions = listOf(
        CustomAccessibilityAction(label = "更新") {
          if (canRefresh) {
            onRefresh()
            true
          } else {
            false
          }
        },
      )
    },
    content = content,
  )
}
