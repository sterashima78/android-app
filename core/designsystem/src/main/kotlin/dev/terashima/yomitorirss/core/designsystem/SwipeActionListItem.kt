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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

data class SwipeBehavior(
  val normalThreshold: Dp = 92.dp,
  val farThreshold: Dp = 176.dp,
  val farThresholdFraction: Float? = null,
  val hapticOnFarTransition: Boolean = false,
  val resistFarTransition: Boolean = false,
) {
  companion object {
    val Default = SwipeBehavior()

    val DeliberateFarAction = SwipeBehavior(
      normalThreshold = 76.dp,
      farThresholdFraction = 0.65f,
      hapticOnFarTransition = true,
      resistFarTransition = true,
    )
  }
}

@Composable
fun LazyItemScope.SwipeActionListItem(
  itemKey: Any,
  left: SwipeAction? = null,
  farLeft: SwipeAction? = null,
  right: SwipeAction? = null,
  farRight: SwipeAction? = null,
  leftBehavior: SwipeBehavior = SwipeBehavior.Default,
  rightBehavior: SwipeBehavior = SwipeBehavior.Default,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  var rowWidth by remember(itemKey) { mutableFloatStateOf(1f) }
  var dragOffset by remember(itemKey) { mutableFloatStateOf(0f) }
  var dragging by remember(itemKey) { mutableStateOf(false) }
  var committing by remember(itemKey) { mutableStateOf(false) }
  var farLeftActive by remember(itemKey) { mutableStateOf(false) }
  var farRightActive by remember(itemKey) { mutableStateOf(false) }
  val currentLeft by rememberUpdatedState(left)
  val currentFarLeft by rememberUpdatedState(farLeft)
  val currentRight by rememberUpdatedState(right)
  val currentFarRight by rememberUpdatedState(farRight)
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current
  val leftNormalThreshold = with(density) { leftBehavior.normalThreshold.toPx() }
  val rightNormalThreshold = with(density) { rightBehavior.normalThreshold.toPx() }
  val leftFarThreshold = resolveFarThreshold(
    rowWidth = rowWidth,
    fixedThreshold = with(density) { leftBehavior.farThreshold.toPx() },
    fraction = leftBehavior.farThresholdFraction,
  )
  val rightFarThreshold = resolveFarThreshold(
    rowWidth = rowWidth,
    fixedThreshold = with(density) { rightBehavior.farThreshold.toPx() },
    fraction = rightBehavior.farThresholdFraction,
  )
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
    farLeft != null && animatedOffset <= -leftFarThreshold -> farLeft
    animatedOffset < 0 -> left
    farRight != null && animatedOffset >= rightFarThreshold -> farRight
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
        .pointerInput(itemKey, left?.label, farLeft?.label, right?.label, farRight?.label, leftBehavior, rightBehavior) {
          detectHorizontalDragGestures(
            onDragStart = {
              if (!committing) {
                dragging = true
                farLeftActive = false
                farRightActive = false
              }
            },
            onDragCancel = {
              dragging = false
              farLeftActive = false
              farRightActive = false
              dragOffset = 0f
            },
            onHorizontalDrag = { change, amount ->
              if (!committing) {
                change.consume()
                val rawNext = dragOffset + amount
                val movingLeft = rawNext < 0f
                val adjustedAmount = applyFarTransitionResistance(
                  offset = dragOffset,
                  delta = amount,
                  farThreshold = if (movingLeft) leftFarThreshold else rightFarThreshold,
                  towardNegative = movingLeft,
                  enabled = if (movingLeft) {
                    currentFarLeft != null && leftBehavior.resistFarTransition
                  } else {
                    currentFarRight != null && rightBehavior.resistFarTransition
                  },
                )
                val next = dragOffset + adjustedAmount
                val canMove =
                  (next < 0 && (currentLeft != null || currentFarLeft != null)) ||
                    (next > 0 && (currentRight != null || currentFarRight != null))
                dragOffset = if (canMove) {
                  next.coerceIn(-rowWidth * MAX_DRAG_FRACTION, rowWidth * MAX_DRAG_FRACTION)
                } else {
                  next * UNSUPPORTED_DIRECTION_RESISTANCE
                }

                val nowFarLeft = currentFarLeft != null && dragOffset <= -leftFarThreshold
                val nowFarRight = currentFarRight != null && dragOffset >= rightFarThreshold
                if (!farLeftActive && nowFarLeft && leftBehavior.hapticOnFarTransition) {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (!farRightActive && nowFarRight && rightBehavior.hapticOnFarTransition) {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                farLeftActive = nowFarLeft
                farRightActive = nowFarRight
              }
            },
            onDragEnd = {
              dragging = false
              val choice = when (
                resolveSwipeCommit(
                  offset = dragOffset,
                  normalThreshold = if (dragOffset < 0) leftNormalThreshold else rightNormalThreshold,
                  farThreshold = if (dragOffset < 0) leftFarThreshold else rightFarThreshold,
                  hasLeft = currentLeft != null,
                  hasFarLeft = currentFarLeft != null,
                  hasRight = currentRight != null,
                  hasFarRight = currentFarRight != null,
                )
              ) {
                SwipeCommit.LEFT -> currentLeft
                SwipeCommit.FAR_LEFT -> currentFarLeft
                SwipeCommit.RIGHT -> currentRight
                SwipeCommit.FAR_RIGHT -> currentFarRight
                SwipeCommit.NONE -> null
              }
              farLeftActive = false
              farRightActive = false
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

internal enum class SwipeCommit { NONE, LEFT, FAR_LEFT, RIGHT, FAR_RIGHT }

internal fun resolveSwipeCommit(
  offset: Float,
  normalThreshold: Float,
  farThreshold: Float,
  hasLeft: Boolean,
  hasRight: Boolean,
  hasFarRight: Boolean,
  hasFarLeft: Boolean = false,
): SwipeCommit = when {
  hasFarLeft && offset <= -farThreshold -> SwipeCommit.FAR_LEFT
  hasLeft && offset <= -normalThreshold -> SwipeCommit.LEFT
  hasFarRight && offset >= farThreshold -> SwipeCommit.FAR_RIGHT
  hasRight && offset >= normalThreshold -> SwipeCommit.RIGHT
  else -> SwipeCommit.NONE
}

internal fun resolveFarThreshold(
  rowWidth: Float,
  fixedThreshold: Float,
  fraction: Float?,
): Float = fraction
  ?.coerceIn(0f, MAX_DRAG_FRACTION)
  ?.let { rowWidth * it }
  ?: fixedThreshold

internal fun applyFarTransitionResistance(
  offset: Float,
  delta: Float,
  farThreshold: Float,
  towardNegative: Boolean,
  enabled: Boolean,
): Float {
  if (!enabled || farThreshold <= 0f) return delta
  val movingTowardFar = if (towardNegative) delta < 0f else delta > 0f
  if (!movingTowardFar) return delta

  val distance = kotlin.math.abs(offset)
  val resistanceStart = farThreshold * FAR_RESISTANCE_START_FRACTION
  return if (distance >= resistanceStart && distance < farThreshold) {
    delta * FAR_RESISTANCE_FACTOR
  } else {
    delta
  }
}

private const val MAX_DRAG_FRACTION = 0.95f
private const val FAR_RESISTANCE_START_FRACTION = 0.82f
private const val FAR_RESISTANCE_FACTOR = 0.42f
private const val UNSUPPORTED_DIRECTION_RESISTANCE = 0.15f
private const val DISMISS_OFFSET_FRACTION = 1.15f
private const val ACTION_DELAY_MILLIS = 145L
private const val RESET_DELAY_MILLIS = 90L
