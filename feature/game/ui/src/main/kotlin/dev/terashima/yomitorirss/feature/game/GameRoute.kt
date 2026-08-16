package dev.terashima.yomitorirss.feature.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        modifier = Modifier.padding(16.dp),
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
        modifier = Modifier.padding(16.dp),
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
        modifier = Modifier.padding(16.dp),
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
