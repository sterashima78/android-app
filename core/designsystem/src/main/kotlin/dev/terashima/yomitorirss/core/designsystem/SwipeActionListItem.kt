@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.terashima.yomitorirss.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SwipeAction(
  val label: String,
  val color: Color,
  val dismissesItem: Boolean = true,
  val onCommit: () -> Unit,
)

@Composable
fun LazyItemScope.SwipeActionListItem(
  itemKey: Any,
  left: SwipeAction? = null,
  right: SwipeAction? = null,
  farRight: SwipeAction? = null,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  var rowWidth by remember(itemKey) { mutableFloatStateOf(1f) }
  var dragOffset by remember(itemKey) { mutableFloatStateOf(0f) }
  var dragging by remember(itemKey) { mutableStateOf(false) }
  var committing by remember(itemKey) { mutableStateOf(false) }
  val currentLeft by rememberUpdatedState(left)
  val currentRight by rememberUpdatedState(right)
  val currentFarRight by rememberUpdatedState(farRight)
  val density = LocalDensity.current
  val normalThreshold = with(density) { NORMAL_THRESHOLD.toPx() }
  val farThreshold = with(density) { FAR_THRESHOLD.toPx() }
  val animatedOffset by animateFloatAsState(
    targetValue = dragOffset,
    animationSpec = if (dragging) snap() else spring(
      dampingRatio = Spring.DampingRatioNoBouncy,
      stiffness = Spring.StiffnessMediumLow,
    ),
    label = "shared-swipe-item",
  )
  val scope = rememberCoroutineScope()
  val visibleChoice = when {
    animatedOffset < 0 -> left
    farRight != null && animatedOffset >= farThreshold -> farRight
    animatedOffset > 0 -> right
    else -> null
  }
  val actionAlignment = if (animatedOffset < 0) Alignment.CenterEnd else Alignment.CenterStart

  Box(
    modifier = modifier
      .animateItem()
      .fillMaxWidth()
      .padding(horizontal = 10.dp, vertical = 3.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(visibleChoice?.color?.copy(alpha = 0.9f) ?: MaterialTheme.colorScheme.surfaceVariant),
  ) {
    visibleChoice?.let { choice ->
      Text(
        choice.label,
        modifier = Modifier.align(actionAlignment).padding(horizontal = 22.dp),
        color = MaterialTheme.colorScheme.background,
        fontWeight = FontWeight.Bold,
      )
    }
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .onSizeChanged { rowWidth = it.width.toFloat().coerceAtLeast(1f) }
        .offset { IntOffset(animatedOffset.roundToInt(), 0) }
        .pointerInput(itemKey, left?.label, right?.label, farRight?.label) {
          detectHorizontalDragGestures(
            onDragStart = { if (!committing) dragging = true },
            onDragCancel = {
              dragging = false
              dragOffset = 0f
            },
            onHorizontalDrag = { change, amount ->
              if (!committing) {
                change.consume()
                val next = dragOffset + amount
                val canMove =
                  (next < 0 && currentLeft != null) ||
                    (next > 0 && (currentRight != null || currentFarRight != null))
                dragOffset = if (canMove) {
                  next.coerceIn(-rowWidth * MAX_DRAG_FRACTION, rowWidth * MAX_DRAG_FRACTION)
                } else {
                  next * UNSUPPORTED_DIRECTION_RESISTANCE
                }
              }
            },
            onDragEnd = {
              dragging = false
              val choice = when (
                resolveSwipeCommit(
                  offset = dragOffset,
                  normalThreshold = normalThreshold,
                  farThreshold = farThreshold,
                  hasLeft = currentLeft != null,
                  hasRight = currentRight != null,
                  hasFarRight = currentFarRight != null,
                )
              ) {
                SwipeCommit.LEFT -> currentLeft
                SwipeCommit.RIGHT -> currentRight
                SwipeCommit.FAR_RIGHT -> currentFarRight
                SwipeCommit.NONE -> null
              }
              if (choice == null) {
                dragOffset = 0f
              } else {
                committing = true
                dragOffset = if (dragOffset < 0) -rowWidth * DISMISS_OFFSET_FRACTION else rowWidth * DISMISS_OFFSET_FRACTION
                scope.launch {
                  delay(ACTION_DELAY_MILLIS)
                  choice.onCommit()
                  if (!choice.dismissesItem) {
                    delay(RESET_DELAY_MILLIS)
                    committing = false
                    dragOffset = 0f
                  }
                }
              }
            },
          )
        },
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(14.dp),
    ) {
      content()
    }
  }
}

internal enum class SwipeCommit { NONE, LEFT, RIGHT, FAR_RIGHT }

internal fun resolveSwipeCommit(
  offset: Float,
  normalThreshold: Float,
  farThreshold: Float,
  hasLeft: Boolean,
  hasRight: Boolean,
  hasFarRight: Boolean,
): SwipeCommit = when {
  hasLeft && offset <= -normalThreshold -> SwipeCommit.LEFT
  hasFarRight && offset >= farThreshold -> SwipeCommit.FAR_RIGHT
  hasRight && offset >= normalThreshold -> SwipeCommit.RIGHT
  else -> SwipeCommit.NONE
}

private val NORMAL_THRESHOLD = 92.dp
private val FAR_THRESHOLD = 176.dp
private const val MAX_DRAG_FRACTION = 0.95f
private const val UNSUPPORTED_DIRECTION_RESISTANCE = 0.15f
private const val DISMISS_OFFSET_FRACTION = 1.15f
private const val ACTION_DELAY_MILLIS = 145L
private const val RESET_DELAY_MILLIS = 90L
