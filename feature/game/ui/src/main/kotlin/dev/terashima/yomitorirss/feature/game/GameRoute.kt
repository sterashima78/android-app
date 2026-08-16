package dev.terashima.yomitorirss.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class GameScreen {
  LIST,
  SUDOKU,
  KLONDIKE,
}

@Composable
fun GameRoute(
  modifier: Modifier = Modifier,
  sudokuViewModel: SudokuViewModel = viewModel(),
  klondikeViewModel: KlondikeViewModel = viewModel(),
) {
  var screen by rememberSaveable { mutableStateOf(GameScreen.LIST.name) }

  when (GameScreen.valueOf(screen)) {
    GameScreen.LIST -> GameListScreen(
      modifier = modifier,
      onOpenSudoku = { screen = GameScreen.SUDOKU.name },
      onOpenKlondike = { screen = GameScreen.KLONDIKE.name },
    )

    GameScreen.SUDOKU -> {
      val state by sudokuViewModel.state.collectAsState()
      SudokuScreen(
        modifier = modifier,
        state = state,
        onBack = { screen = GameScreen.LIST.name },
        onNewGame = sudokuViewModel::newGame,
        onSelectCell = sudokuViewModel::selectCell,
        onEnterNumber = sudokuViewModel::enterNumber,
        onClearCell = sudokuViewModel::clearSelectedCell,
      )
    }

    GameScreen.KLONDIKE -> KlondikeRoute(
      modifier = modifier,
      viewModel = klondikeViewModel,
      onBack = { screen = GameScreen.LIST.name },
    )
  }
}

@Composable
private fun GameListScreen(
  modifier: Modifier,
  onOpenSudoku: () -> Unit,
  onOpenKlondike: () -> Unit,
) {
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("一人用ゲーム", style = MaterialTheme.typography.titleLarge)
    Text(
      "端末だけで遊べるゲームをまとめています。",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(onClick = onOpenSudoku) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("数独", style = MaterialTheme.typography.titleMedium)
          Text(
            "9×9 の盤面を数字で埋める一人用パズル",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    Card(onClick = onOpenKlondike) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("♠", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.size(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("クロンダイク", style = MaterialTheme.typography.titleMedium)
          Text(
            "52枚のトランプを4組の組札へ揃える定番ソリティア",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun SudokuScreen(
  modifier: Modifier,
  state: SudokuGameState,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
  onSelectCell: (Int) -> Unit,
  onEnterNumber: (Int) -> Unit,
  onClearCell: () -> Unit,
) {
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text("数独", style = MaterialTheme.typography.headlineSmall)
        Text(
          "ミス: ${state.mistakes}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(onClick = onBack) { Text("ゲーム一覧") }
    }

    SudokuBoard(
      state = state,
      onSelectCell = onSelectCell,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    if (state.isCompleted) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("完成しました", style = MaterialTheme.typography.titleMedium)
          Text("新しい問題でもう一度遊べます。")
          Button(onClick = onNewGame) { Text("新しい問題") }
        }
      }
    } else {
      NumberPad(
        onEnterNumber = onEnterNumber,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      ) {
        OutlinedButton(onClick = onClearCell) { Text("消去") }
        Button(onClick = onNewGame) { Text("新しい問題") }
      }
      Text(
        "誤った数字は盤面には入りません。ミス回数として記録します。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun SudokuBoard(
  state: SudokuGameState,
  onSelectCell: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val thinLine = MaterialTheme.colorScheme.outlineVariant
  val thickLine = MaterialTheme.colorScheme.onSurface
  val selectedIndex = state.selectedIndex
  val selectedValue = selectedIndex?.let(state.entries::get)?.takeIf { it != 0 }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .widthIn(max = 480.dp)
      .aspectRatio(1f)
      .drawWithContent {
        drawContent()
        val cellSize = size.width / 9f
        for (index in 0..9) {
          val lineColor = if (index % 3 == 0) thickLine else thinLine
          val stroke = if (index % 3 == 0) 2.dp.toPx() else 1.dp.toPx()
          val offset = index * cellSize
          drawLine(lineColor, Offset(offset, 0f), Offset(offset, size.height), stroke)
          drawLine(lineColor, Offset(0f, offset), Offset(size.width, offset), stroke)
        }
      },
  ) {
    Column(Modifier.fillMaxSize()) {
      for (row in 0 until 9) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
          for (column in 0 until 9) {
            val index = row * 9 + column
            val value = state.entries[index]
            val isSelected = selectedIndex == index
            val related = selectedIndex?.let { selected ->
              val selectedRow = selected / 9
              val selectedColumn = selected % 9
              row == selectedRow ||
                column == selectedColumn ||
                (row / 3 == selectedRow / 3 && column / 3 == selectedColumn / 3)
            } ?: false
            val sameValue = selectedValue != null && value == selectedValue
            val background = when {
              isSelected -> MaterialTheme.colorScheme.secondaryContainer
              sameValue -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
              related -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
              else -> Color.Transparent
            }
            val description = buildString {
              append("${row + 1}行${column + 1}列")
              if (value == 0) append("、空白") else append("、$value")
              if (state.puzzle.isGiven(index)) append("、固定")
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(background)
                .clickable { onSelectCell(index) }
                .semantics { contentDescription = description },
              contentAlignment = Alignment.Center,
            ) {
              if (value != 0) {
                Text(
                  text = value.toString(),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = if (state.puzzle.isGiven(index)) FontWeight.Bold else FontWeight.Normal,
                  color = if (state.puzzle.isGiven(index)) {
                    MaterialTheme.colorScheme.onSurface
                  } else {
                    MaterialTheme.colorScheme.primary
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NumberPad(
  onEnterNumber: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.widthIn(max = 360.dp).fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    for (row in 0 until 3) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for (column in 0 until 3) {
          val number = row * 3 + column + 1
          FilledTonalButton(
            onClick = { onEnterNumber(number) },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 12.dp),
          ) {
            Text(number.toString())
          }
        }
      }
    }
  }
}
