package dev.terashima.yomitorirss.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
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
  SPIDER,
}

@Composable
fun GameRoute(
  modifier: Modifier = Modifier,
  sudokuViewModel: SudokuViewModel = viewModel(),
  klondikeViewModel: KlondikeViewModel = viewModel(),
  spiderViewModel: SpiderViewModel = viewModel(),
) {
  var screen by rememberSaveable { mutableStateOf(GameScreen.LIST.name) }

  when (GameScreen.valueOf(screen)) {
    GameScreen.LIST -> GameListScreen(
      modifier = modifier,
      onOpenSudoku = { screen = GameScreen.SUDOKU.name },
      onOpenKlondike = { screen = GameScreen.KLONDIKE.name },
      onOpenSpider = { screen = GameScreen.SPIDER.name },
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

    GameScreen.SPIDER -> SpiderRoute(
      modifier = modifier,
      viewModel = spiderViewModel,
      onBack = { screen = GameScreen.LIST.name },
    )
  }
}

@Composable
private fun GameListScreen(
  modifier: Modifier,
  onOpenSudoku: () -> Unit,
  onOpenKlondike: () -> Unit,
  onOpenSpider: () -> Unit,
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
    Card(onClick = onOpenSpider) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("🕷", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.size(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("スパイダーソリティア", style = MaterialTheme.typography.titleMedium)
          Text(
            "104枚を並べ替えて同一スートのKからAを8組完成させるソリティア",
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
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      SudokuHeader(
        mistakes = state.mistakes,
        onBack = onBack,
        onNewGame = onNewGame,
      )

      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        val boardSize = minOf(maxWidth, maxHeight).coerceAtMost(520.dp)
        Surface(
          modifier = Modifier.size(boardSize),
          shape = RoundedCornerShape(18.dp),
          tonalElevation = 2.dp,
          shadowElevation = 6.dp,
          color = MaterialTheme.colorScheme.surface,
        ) {
          SudokuBoard(
            state = state,
            onSelectCell = onSelectCell,
            modifier = Modifier
              .fillMaxSize()
              .padding(4.dp),
          )
        }
      }

      SudokuInputDock(
        state = state,
        onEnterNumber = onEnterNumber,
        onClearCell = onClearCell,
      )
    }

    AnimatedVisibility(
      visible = state.isCompleted,
      modifier = Modifier.align(Alignment.Center),
      enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 }),
      exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 5 }),
    ) {
      Card(
        modifier = Modifier
          .padding(28.dp)
          .widthIn(max = 320.dp),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("完成", style = MaterialTheme.typography.headlineSmall)
          Text(
            "すべてのマスが揃いました。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
            Text("次の問題")
          }
        }
      }
    }
  }
}

@Composable
private fun SudokuHeader(
  mistakes: Int,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) {
      Text("‹ ゲーム")
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        "数独",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        "ミス $mistakes",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TextButton(onClick = onNewGame, contentPadding = PaddingValues(horizontal = 8.dp)) {
      Text("新しい問題")
    }
  }
}

@Composable
private fun SudokuInputDock(
  state: SudokuGameState,
  onEnterNumber: (Int) -> Unit,
  onClearCell: () -> Unit,
) {
  val selectedIndex = state.selectedIndex
  val isEditable = selectedIndex != null &&
    !state.puzzle.isGiven(selectedIndex) &&
    !state.isCompleted

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(106.dp),
    contentAlignment = Alignment.Center,
  ) {
    AnimatedVisibility(
      visible = isEditable,
      enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
      exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
    ) {
      SudokuNumberPad(
        state = state,
        selectedIndex = requireNotNull(selectedIndex),
        onEnterNumber = onEnterNumber,
        onClearCell = onClearCell,
      )
    }

    AnimatedVisibility(
      visible = !isEditable && !state.isCompleted,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Text(
        text = if (selectedIndex == null) {
          "空いているマスを選択"
        } else {
          "固定数字です。空いているマスを選択してください"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SudokuBoard(
  state: SudokuGameState,
  onSelectCell: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val thinLine = MaterialTheme.colorScheme.outlineVariant
  val thickLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
  val selectedIndex = state.selectedIndex
  val selectedValue = selectedIndex?.let(state.entries::get)?.takeIf { it != 0 }
  val boardShape = RoundedCornerShape(14.dp)

  Box(
    modifier = modifier
      .clip(boardShape)
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), boardShape)
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
            val targetBackground = when {
              isSelected -> MaterialTheme.colorScheme.secondaryContainer
              sameValue -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
              related -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
              else -> Color.Transparent
            }
            val background by animateColorAsState(
              targetValue = targetBackground,
              label = "sudoku-cell-background",
            )
            val description = buildString {
              append("${row + 1}行${column + 1}列")
              if (value == 0) append("、空白") else append("、$value")
              if (state.puzzle.isGiven(index)) append("、固定") else append("、入力可能")
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
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = if (state.puzzle.isGiven(index)) FontWeight.Bold else FontWeight.SemiBold,
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
private fun SudokuNumberPad(
  state: SudokuGameState,
  selectedIndex: Int,
  onEnterNumber: (Int) -> Unit,
  onClearCell: () -> Unit,
) {
  Column(
    modifier = Modifier
      .widthIn(max = 420.dp)
      .fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    SudokuNumberRow(
      numbers = 1..5,
      state = state,
      selectedIndex = selectedIndex,
      onEnterNumber = onEnterNumber,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      for (number in 6..9) {
        SudokuNumberButton(
          number = number,
          enabled = isSudokuNumberAvailable(state, selectedIndex, number),
          onClick = { onEnterNumber(number) },
          modifier = Modifier.weight(1f),
        )
      }
      FilledTonalButton(
        onClick = onClearCell,
        enabled = state.entries[selectedIndex] != 0,
        modifier = Modifier
          .weight(1f)
          .height(46.dp),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text("消去", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun SudokuNumberRow(
  numbers: IntRange,
  state: SudokuGameState,
  selectedIndex: Int,
  onEnterNumber: (Int) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    for (number in numbers) {
      SudokuNumberButton(
        number = number,
        enabled = isSudokuNumberAvailable(state, selectedIndex, number),
        onClick = { onEnterNumber(number) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun SudokuNumberButton(
  number: Int,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FilledTonalButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(46.dp),
    contentPadding = PaddingValues(0.dp),
    shape = RoundedCornerShape(12.dp),
  ) {
    Text(
      text = number.toString(),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

private fun isSudokuNumberAvailable(
  state: SudokuGameState,
  selectedIndex: Int,
  number: Int,
): Boolean {
  val selectedRow = selectedIndex / 9
  val selectedColumn = selectedIndex % 9

  return state.entries.indices.none { index ->
    if (index == selectedIndex || state.entries[index] != number) return@none false
    val row = index / 9
    val column = index % 9
    row == selectedRow ||
      column == selectedColumn ||
      (row / 3 == selectedRow / 3 && column / 3 == selectedColumn / 3)
  }
}
