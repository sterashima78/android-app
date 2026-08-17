package dev.terashima.yomitorirss.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private enum class NonogramInputMode { FILL, MARK }

@Composable
internal fun NonogramScreen(
  modifier: Modifier,
  state: NonogramGameState,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
  onFill: (Int) -> Unit,
  onMark: (Int) -> Unit,
) {
  var inputMode by rememberSaveable { mutableStateOf(NonogramInputMode.FILL.name) }

  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      NonogramHeader(onBack, onNewGame)

      BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        val clueWidth = 58.dp
        val clueHeight = 70.dp
        val boardSize = minOf(maxWidth - clueWidth, maxHeight - clueHeight, 410.dp).coerceAtLeast(1.dp)
        NonogramBoard(
          state = state,
          boardSize = boardSize,
          clueWidth = clueWidth,
          clueHeight = clueHeight,
          onCellClick = { index ->
            if (inputMode == NonogramInputMode.FILL.name) onFill(index) else onMark(index)
          },
        )
      }

      Text(
        "数字は連続して塗るマス数です。空白と判断したマスには × を付けられます。",
        modifier = Modifier.align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      ) {
        FilledTonalButton(
          onClick = { inputMode = NonogramInputMode.FILL.name },
          enabled = inputMode != NonogramInputMode.FILL.name,
        ) { Text(if (inputMode == NonogramInputMode.FILL.name) "● 塗る" else "塗る") }
        FilledTonalButton(
          onClick = { inputMode = NonogramInputMode.MARK.name },
          enabled = inputMode != NonogramInputMode.MARK.name,
        ) { Text(if (inputMode == NonogramInputMode.MARK.name) "● ×印" else "×印") }
      }
    }

    AnimatedVisibility(visible = state.isCompleted, modifier = Modifier.align(Alignment.Center)) {
      Card(modifier = Modifier.padding(28.dp).widthIn(max = 320.dp)) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("完成", style = MaterialTheme.typography.headlineSmall)
          Text("すべてのマスがヒントと一致しました。", style = MaterialTheme.typography.bodyMedium)
          Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("次の問題") }
        }
      }
    }
  }
}

@Composable
private fun NonogramHeader(onBack: () -> Unit, onNewGame: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("‹ ゲーム") }
    Column(modifier = Modifier.weight(1f)) {
      Text("ノノグラム", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        "5×5 ピクロス",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TextButton(onClick = onNewGame, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("新しい問題") }
  }
}

@Composable
private fun NonogramBoard(
  state: NonogramGameState,
  boardSize: Dp,
  clueWidth: Dp,
  clueHeight: Dp,
  onCellClick: (Int) -> Unit,
) {
  val puzzle = state.puzzle
  val shape = RoundedCornerShape(14.dp)

  Column(modifier = Modifier.width(clueWidth + boardSize).height(clueHeight + boardSize)) {
    Row(
      modifier = Modifier.width(clueWidth + boardSize).height(clueHeight),
      verticalAlignment = Alignment.Bottom,
    ) {
      Box(Modifier.width(clueWidth))
      puzzle.columnClues.forEach { clues ->
        Box(
          modifier = Modifier.weight(1f).height(clueHeight).padding(bottom = 6.dp),
          contentAlignment = Alignment.BottomCenter,
        ) {
          Text(
            clues.joinToString("\n"),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }

    Row(modifier = Modifier.width(clueWidth + boardSize).height(boardSize)) {
      Column(modifier = Modifier.width(clueWidth).height(boardSize)) {
        puzzle.rowClues.forEach { clues ->
          Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(end = 6.dp),
            contentAlignment = Alignment.CenterEnd,
          ) {
            Text(
              clues.joinToString(" "),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }

      Surface(
        modifier = Modifier.size(boardSize).clip(shape),
        shape = shape,
        tonalElevation = 2.dp,
        shadowElevation = 5.dp,
      ) {
        Column(Modifier.fillMaxSize()) {
          for (row in 0 until puzzle.height) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
              for (column in 0 until puzzle.width) {
                val index = row * puzzle.width + column
                val cell = state.cells[index]
                val background = when (cell) {
                  NonogramCellState.FILLED -> MaterialTheme.colorScheme.primary
                  NonogramCellState.UNKNOWN -> MaterialTheme.colorScheme.surface
                  NonogramCellState.MARKED -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(background)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .clickable(enabled = !state.isCompleted) { onCellClick(index) },
                  contentAlignment = Alignment.Center,
                ) {
                  if (cell == NonogramCellState.MARKED) {
                    Text(
                      "×",
                      style = MaterialTheme.typography.titleLarge,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
