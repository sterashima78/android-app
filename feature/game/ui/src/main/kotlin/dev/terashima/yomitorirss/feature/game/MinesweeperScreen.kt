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
import androidx.compose.ui.unit.dp

private enum class MinesweeperInputMode { REVEAL, FLAG }

@Composable
internal fun MinesweeperScreen(
  modifier: Modifier,
  state: MinesweeperState,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
  onReveal: (Int) -> Unit,
  onToggleFlag: (Int) -> Unit,
) {
  var inputMode by rememberSaveable { mutableStateOf(MinesweeperInputMode.REVEAL.name) }

  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      MinesweeperHeader(state, onBack, onNewGame)
      BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        val boardSize = minOf(maxWidth, maxHeight).coerceAtMost(520.dp)
        MinesweeperBoard(
          state = state,
          onCellClick = { index ->
            if (inputMode == MinesweeperInputMode.REVEAL.name) onReveal(index) else onToggleFlag(index)
          },
          modifier = Modifier.size(boardSize),
        )
      }
      Text(
        if (state.initialized) {
          "数字は周囲8マスにある地雷の数です。"
        } else {
          "最初に開くマスとその周囲には地雷を配置しません。"
        },
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
          onClick = { inputMode = MinesweeperInputMode.REVEAL.name },
          enabled = inputMode != MinesweeperInputMode.REVEAL.name,
        ) { Text(if (inputMode == MinesweeperInputMode.REVEAL.name) "● 開く" else "開く") }
        FilledTonalButton(
          onClick = { inputMode = MinesweeperInputMode.FLAG.name },
          enabled = inputMode != MinesweeperInputMode.FLAG.name,
        ) { Text(if (inputMode == MinesweeperInputMode.FLAG.name) "● ⚑ 旗" else "⚑ 旗") }
      }
    }

    AnimatedVisibility(
      visible = state.status != MinesweeperStatus.PLAYING,
      modifier = Modifier.align(Alignment.Center),
    ) {
      Card(modifier = Modifier.padding(28.dp).widthIn(max = 320.dp)) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            if (state.status == MinesweeperStatus.WON) "クリア" else "地雷でした",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            if (state.status == MinesweeperStatus.WON) {
              "地雷以外のマスをすべて開きました。"
            } else {
              "新しい盤面でもう一度挑戦できます。"
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
          )
          Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) { Text("もう一度") }
        }
      }
    }
  }
}

@Composable
private fun MinesweeperHeader(state: MinesweeperState, onBack: () -> Unit, onNewGame: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("‹ ゲーム") }
    Column(modifier = Modifier.weight(1f)) {
      Text("マインスイーパー", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        "9×9 · 地雷 ${state.mineCount} · 旗 残り ${state.flagsRemaining}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TextButton(onClick = onNewGame, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("新しい盤面") }
  }
}

@Composable
private fun MinesweeperBoard(
  state: MinesweeperState,
  onCellClick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(16.dp)
  Surface(
    modifier = modifier.clip(shape),
    shape = shape,
    tonalElevation = 2.dp,
    shadowElevation = 5.dp,
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(3.dp)) {
      for (row in 0 until state.height) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
          for (column in 0 until state.width) {
            val index = row * state.width + column
            MinesweeperCellView(
              cell = state.cells[index],
              enabled = state.status == MinesweeperStatus.PLAYING,
              onClick = { onCellClick(index) },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MinesweeperCellView(
  cell: MinesweeperCell,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val background = when (cell.visibility) {
    MinesweeperVisibility.HIDDEN -> MaterialTheme.colorScheme.secondaryContainer
    MinesweeperVisibility.FLAGGED -> MaterialTheme.colorScheme.tertiaryContainer
    MinesweeperVisibility.REVEALED -> if (cell.isMine) {
      MaterialTheme.colorScheme.errorContainer
    } else {
      MaterialTheme.colorScheme.surface
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(1.dp)
      .background(background, RoundedCornerShape(5.dp))
      .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
      .clickable(enabled = enabled) { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    val label = when {
      cell.visibility == MinesweeperVisibility.FLAGGED -> "⚑"
      cell.visibility != MinesweeperVisibility.REVEALED -> ""
      cell.isMine -> "●"
      cell.adjacentMines > 0 -> cell.adjacentMines.toString()
      else -> ""
    }
    if (label.isNotEmpty()) {
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = when {
          cell.isMine -> MaterialTheme.colorScheme.onErrorContainer
          cell.visibility == MinesweeperVisibility.FLAGGED -> MaterialTheme.colorScheme.onTertiaryContainer
          cell.adjacentMines >= 4 -> MaterialTheme.colorScheme.error
          cell.adjacentMines >= 2 -> MaterialTheme.colorScheme.tertiary
          else -> MaterialTheme.colorScheme.primary
        },
      )
    }
  }
}
