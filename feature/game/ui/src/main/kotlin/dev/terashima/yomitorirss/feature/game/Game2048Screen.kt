package dev.terashima.yomitorirss.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val GAME_2048_ANIMATION_MOVE_MS = 170
private const val GAME_2048_ANIMATION_SETTLE_MS = 190
private const val GAME_2048_ANIMATION_BLOCKED_OUT_MS = 65
private const val GAME_2048_ANIMATION_BLOCKED_BACK_MS = 105

@Composable
internal fun Game2048Screen(
  modifier: Modifier,
  state: Game2048UiState,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
  onMove: (Game2048Direction) -> Unit,
  onAnimationFinished: (Long) -> Unit,
) {
  val game = state.game
  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Game2048Header(
        score = game.score,
        scoreDelta = state.transition?.scoreDelta ?: 0,
        onBack = onBack,
        onNewGame = onNewGame,
      )
      BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        val boardSize = minOf(maxWidth, maxHeight).coerceAtMost(520.dp)
        Game2048Board(
          state = state,
          onMove = onMove,
          onAnimationFinished = onAnimationFinished,
          modifier = Modifier.size(boardSize),
        )
      }
      Text(
        "盤面を上下左右へスワイプ。タイルの移動と結合を追って次の手を選べます。",
        modifier = Modifier.align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Game2048Controls(
        onMove = onMove,
        enabled = state.transition == null,
      )
    }

    AnimatedVisibility(
      visible = state.transition == null && game.isGameOver,
      modifier = Modifier.align(Alignment.Center),
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Card(modifier = Modifier.padding(28.dp).widthIn(max = 320.dp)) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("ゲームオーバー", style = MaterialTheme.typography.headlineSmall)
          Text("スコア ${game.score}", style = MaterialTheme.typography.titleMedium)
          Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("もう一度") }
        }
      }
    }
  }
}

@Composable
private fun Game2048Header(
  score: Int,
  scoreDelta: Int,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
) {
  val animatedScore by animateIntAsState(
    targetValue = score,
    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
    label = "2048-score",
  )

  Row(
    modifier = Modifier.fillMaxWidth().height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("‹ ゲーム") }
    Column(modifier = Modifier.weight(1f)) {
      Text("2048", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          "スコア $animatedScore",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(
          visible = scoreDelta > 0,
          enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
          exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        ) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              "+$scoreDelta",
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
    }
    TextButton(onClick = onNewGame, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("新しいゲーム") }
  }
}

@Composable
private fun Game2048Board(
  state: Game2048UiState,
  onMove: (Game2048Direction) -> Unit,
  onAnimationFinished: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val transition = state.transition
  val movementProgress = remember(state.transitionId) {
    Animatable(if (transition == null) 1f else 0f)
  }
  val settleProgress = remember(state.transitionId) {
    Animatable(if (transition == null) 1f else 0f)
  }
  val blockedProgress = remember(state.transitionId) { Animatable(0f) }
  var movementFinished by remember(state.transitionId) {
    mutableStateOf(transition == null || !transition.changed)
  }
  var drag by remember { mutableStateOf(Offset.Zero) }

  LaunchedEffect(state.transitionId, transition) {
    if (transition == null) return@LaunchedEffect

    if (!transition.changed) {
      blockedProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(GAME_2048_ANIMATION_BLOCKED_OUT_MS, easing = FastOutSlowInEasing),
      )
      blockedProgress.animateTo(
        targetValue = 0f,
        animationSpec = tween(GAME_2048_ANIMATION_BLOCKED_BACK_MS, easing = FastOutSlowInEasing),
      )
      onAnimationFinished(state.transitionId)
      return@LaunchedEffect
    }

    movementProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(GAME_2048_ANIMATION_MOVE_MS, easing = FastOutSlowInEasing),
    )
    movementFinished = true
    settleProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(GAME_2048_ANIMATION_SETTLE_MS, easing = FastOutSlowInEasing),
    )
    onAnimationFinished(state.transitionId)
  }

  val blockedDistance = 8.dp * blockedProgress.value
  val nudgeX = when (transition?.direction) {
    Game2048Direction.LEFT -> -blockedDistance
    Game2048Direction.RIGHT -> blockedDistance
    else -> 0.dp
  }
  val nudgeY = when (transition?.direction) {
    Game2048Direction.UP -> -blockedDistance
    Game2048Direction.DOWN -> blockedDistance
    else -> 0.dp
  }
  val shape = RoundedCornerShape(20.dp)

  Surface(
    modifier = modifier
      .offset(x = nudgeX, y = nudgeY)
      .aspectRatio(1f)
      .pointerInput(onMove, state.transitionId, transition == null) {
        if (transition != null) return@pointerInput
        detectDragGestures(
          onDragStart = { drag = Offset.Zero },
          onDragEnd = {
            val threshold = 36.dp.toPx()
            if (abs(drag.x) >= threshold || abs(drag.y) >= threshold) {
              val direction = if (abs(drag.x) > abs(drag.y)) {
                if (drag.x > 0) Game2048Direction.RIGHT else Game2048Direction.LEFT
              } else {
                if (drag.y > 0) Game2048Direction.DOWN else Game2048Direction.UP
              }
              onMove(direction)
            }
            drag = Offset.Zero
          },
          onDragCancel = { drag = Offset.Zero },
          onDrag = { _, amount -> drag += amount },
        )
      },
    shape = shape,
    tonalElevation = 3.dp,
    shadowElevation = 7.dp,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val edge = 7.dp
      val gap = 7.dp
      val tileSize = (maxWidth - edge * 2 - gap * 3) / 4
      val step = tileSize + gap

      repeat(16) { index ->
        Game2048Slot(
          x = cellX(index, edge, step),
          y = cellY(index, edge, step),
          size = tileSize,
        )
      }

      if (transition != null && transition.changed && !movementFinished) {
        transition.movements.forEach { movement ->
          val startX = cellX(movement.fromIndex, edge, step)
          val startY = cellY(movement.fromIndex, edge, step)
          val endX = cellX(movement.toIndex, edge, step)
          val endY = cellY(movement.toIndex, edge, step)
          val progress = movementProgress.value
          Game2048Tile(
            value = movement.value,
            scale = 1f,
            elevated = movement.fromIndex != movement.toIndex,
            modifier = Modifier
              .offset(
                x = startX + (endX - startX) * progress,
                y = startY + (endY - startY) * progress,
              )
              .size(tileSize),
          )
        }
      } else {
        val mergedIndices = transition
          ?.movements
          ?.asSequence()
          ?.filter { it.isMerge }
          ?.map { it.toIndex }
          ?.toSet()
          .orEmpty()

        state.game.tiles.forEachIndexed { index, value ->
          if (value == 0) return@forEachIndexed
          val scale = when {
            transition == null -> 1f
            transition.spawnedIndex == index -> spawnedTileScale(settleProgress.value)
            index in mergedIndices -> mergedTileScale(settleProgress.value)
            else -> 1f
          }
          Game2048Tile(
            value = value,
            scale = scale,
            elevated = index in mergedIndices,
            modifier = Modifier
              .offset(
                x = cellX(index, edge, step),
                y = cellY(index, edge, step),
              )
              .size(tileSize),
          )
        }
      }
    }
  }
}

@Composable
private fun Game2048Slot(
  x: Dp,
  y: Dp,
  size: Dp,
) {
  Box(
    modifier = Modifier
      .offset(x = x, y = y)
      .size(size)
      .background(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        shape = RoundedCornerShape(14.dp),
      )
      .border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
        shape = RoundedCornerShape(14.dp),
      ),
  )
}

@Composable
private fun Game2048Tile(
  value: Int,
  scale: Float,
  elevated: Boolean,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(14.dp)
  val background = when {
    value <= 4 -> MaterialTheme.colorScheme.secondaryContainer
    value <= 16 -> MaterialTheme.colorScheme.tertiaryContainer
    value <= 128 -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.primary
  }
  val textColor = if (value > 128) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

  Box(
    modifier = modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .shadow(if (elevated) 5.dp else 2.dp, shape)
      .background(background, shape)
      .border(
        width = 1.dp,
        color = if (value >= 128) {
          MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
        } else {
          Color.White.copy(alpha = 0.12f)
        },
        shape = shape,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      value.toString(),
      color = textColor,
      fontWeight = FontWeight.Bold,
      style = when {
        value < 100 -> MaterialTheme.typography.headlineMedium
        value < 1000 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
      },
    )
  }
}

private fun cellX(index: Int, edge: Dp, step: Dp): Dp = edge + step * (index % 4)

private fun cellY(index: Int, edge: Dp, step: Dp): Dp = edge + step * (index / 4)

private fun spawnedTileScale(progress: Float): Float =
  if (progress < 0.72f) {
    1.08f * (progress / 0.72f)
  } else {
    1.08f - 0.08f * ((progress - 0.72f) / 0.28f)
  }

private fun mergedTileScale(progress: Float): Float =
  if (progress < 0.45f) {
    0.86f + 0.30f * (progress / 0.45f)
  } else {
    1.16f - 0.16f * ((progress - 0.45f) / 0.55f)
  }

@Composable
private fun Game2048Controls(
  onMove: (Game2048Direction) -> Unit,
  enabled: Boolean,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    FilledTonalButton(
      onClick = { onMove(Game2048Direction.UP) },
      enabled = enabled,
      modifier = Modifier.size(width = 84.dp, height = 42.dp),
      contentPadding = PaddingValues(0.dp),
    ) { Text("↑") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilledTonalButton(
        onClick = { onMove(Game2048Direction.LEFT) },
        enabled = enabled,
        modifier = Modifier.size(width = 84.dp, height = 42.dp),
        contentPadding = PaddingValues(0.dp),
      ) { Text("←") }
      FilledTonalButton(
        onClick = { onMove(Game2048Direction.DOWN) },
        enabled = enabled,
        modifier = Modifier.size(width = 84.dp, height = 42.dp),
        contentPadding = PaddingValues(0.dp),
      ) { Text("↓") }
      FilledTonalButton(
        onClick = { onMove(Game2048Direction.RIGHT) },
        enabled = enabled,
        modifier = Modifier.size(width = 84.dp, height = 42.dp),
        contentPadding = PaddingValues(0.dp),
      ) { Text("→") }
    }
  }
}
