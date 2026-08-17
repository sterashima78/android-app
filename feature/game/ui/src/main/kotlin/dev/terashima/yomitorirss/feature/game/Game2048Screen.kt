package dev.terashima.yomitorirss.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
internal fun Game2048Screen(
  modifier: Modifier,
  state: Game2048State,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
  onMove: (Game2048Direction) -> Unit,
) {
  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Game2048Header(state.score, onBack, onNewGame)
      BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        val boardSize = minOf(maxWidth, maxHeight).coerceAtMost(520.dp)
        Game2048Board(state, onMove, Modifier.size(boardSize))
      }
      Text(
        "盤面を上下左右へスワイプ。下のボタンでも操作できます。",
        modifier = Modifier.align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Game2048Controls(onMove)
    }

    AnimatedVisibility(visible = state.isGameOver, modifier = Modifier.align(Alignment.Center)) {
      Card(modifier = Modifier.padding(28.dp).widthIn(max = 320.dp)) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("ゲームオーバー", style = MaterialTheme.typography.headlineSmall)
          Text("スコア ${state.score}", style = MaterialTheme.typography.titleMedium)
          Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("もう一度") }
        }
      }
    }
  }
}

@Composable
private fun Game2048Header(score: Int, onBack: () -> Unit, onNewGame: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("‹ ゲーム") }
    Column(modifier = Modifier.weight(1f)) {
      Text("2048", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        "スコア $score",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TextButton(onClick = onNewGame, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("新しいゲーム") }
  }
}

@Composable
private fun Game2048Board(
  state: Game2048State,
  onMove: (Game2048Direction) -> Unit,
  modifier: Modifier = Modifier,
) {
  var drag by remember { mutableStateOf(Offset.Zero) }
  val shape = RoundedCornerShape(20.dp)
  Surface(
    modifier = modifier
      .aspectRatio(1f)
      .pointerInput(onMove) {
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
    shadowElevation = 6.dp,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(7.dp),
      verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      for (row in 0 until 4) {
        Row(
          modifier = Modifier.fillMaxWidth().weight(1f),
          horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          for (column in 0 until 4) {
            val value = state.tiles[row * 4 + column]
            Game2048Tile(value, Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun Game2048Tile(value: Int, modifier: Modifier = Modifier) {
  val targetColor = when {
    value == 0 -> MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    value <= 4 -> MaterialTheme.colorScheme.secondaryContainer
    value <= 16 -> MaterialTheme.colorScheme.tertiaryContainer
    value <= 128 -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.primary
  }
  val textColor = if (value > 128) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
  val background by animateColorAsState(targetColor, label = "2048-tile")
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(background, RoundedCornerShape(14.dp))
      .border(
        width = if (value == 0) 1.dp else 0.dp,
        color = if (value == 0) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
      )
      .animateContentSize(),
    contentAlignment = Alignment.Center,
  ) {
    if (value != 0) {
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
}

@Composable
private fun Game2048Controls(onMove: (Game2048Direction) -> Unit) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    FilledTonalButton(
      onClick = { onMove(Game2048Direction.UP) },
      modifier = Modifier.size(width = 84.dp, height = 42.dp),
      contentPadding = PaddingValues(0.dp),
    ) { Text("↑") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilledTonalButton(
        onClick = { onMove(Game2048Direction.LEFT) },
        modifier = Modifier.size(width = 84.dp, height = 42.dp),
        contentPadding = PaddingValues(0.dp),
      ) { Text("←") }
      FilledTonalButton(
        onClick = { onMove(Game2048Direction.DOWN) },
        modifier = Modifier.size(width = 84.dp, height = 42.dp),
        contentPadding = PaddingValues(0.dp),
      ) { Text("↓") }
      FilledTonalButton(
        onClick = { onMove(Game2048Direction.RIGHT) },
        modifier = Modifier.size(width = 84.dp, height = 42.dp),
        contentPadding = PaddingValues(0.dp),
      ) { Text("→") }
    }
  }
}
