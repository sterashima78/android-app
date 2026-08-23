package dev.terashima.yomitorirss.feature.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

internal enum class GameScreen {
  LIST,
  SUDOKU,
  GAME_2048,
  NONOGRAM,
  MINESWEEPER,
  KLONDIKE,
  SPIDER,
}

@Composable
fun GameRoute(
  modifier: Modifier = Modifier,
  sudokuViewModel: SudokuViewModel = viewModel(),
  game2048ViewModel: Game2048ViewModel = viewModel(),
  nonogramViewModel: NonogramViewModel = viewModel(),
  minesweeperViewModel: MinesweeperViewModel = viewModel(),
  klondikeViewModel: KlondikeViewModel = viewModel(),
  spiderViewModel: SpiderViewModel = viewModel(),
) {
  var screen by rememberSaveable { mutableStateOf(GameScreen.LIST.name) }
  val gameScreen = GameScreen.valueOf(screen)

  GameOrientationEffect(gameScreen)

  when (gameScreen) {
    GameScreen.LIST -> GameListScreen(
      modifier = modifier,
      onOpenSudoku = { screen = GameScreen.SUDOKU.name },
      onOpen2048 = { screen = GameScreen.GAME_2048.name },
      onOpenNonogram = { screen = GameScreen.NONOGRAM.name },
      onOpenMinesweeper = { screen = GameScreen.MINESWEEPER.name },
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

    GameScreen.GAME_2048 -> {
      val state by game2048ViewModel.state.collectAsState()
      Game2048Screen(
        modifier = modifier,
        state = state,
        onBack = { screen = GameScreen.LIST.name },
        onNewGame = game2048ViewModel::newGame,
        onMove = game2048ViewModel::move,
        onAnimationFinished = game2048ViewModel::completeTransition,
      )
    }

    GameScreen.NONOGRAM -> {
      val state by nonogramViewModel.state.collectAsState()
      NonogramScreen(
        modifier = modifier,
        state = state,
        onBack = { screen = GameScreen.LIST.name },
        onNewGame = nonogramViewModel::newGame,
        onFill = nonogramViewModel::fill,
        onMark = nonogramViewModel::mark,
      )
    }

    GameScreen.MINESWEEPER -> {
      val state by minesweeperViewModel.state.collectAsState()
      MinesweeperScreen(
        modifier = modifier,
        state = state,
        onBack = { screen = GameScreen.LIST.name },
        onNewGame = minesweeperViewModel::newGame,
        onReveal = minesweeperViewModel::reveal,
        onToggleFlag = minesweeperViewModel::toggleFlag,
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
private fun GameOrientationEffect(screen: GameScreen) {
  val activity = LocalContext.current.findActivity() ?: return

  LaunchedEffect(activity, screen) {
    activity.requestedOrientation = requestedOrientationFor(screen)
  }

  DisposableEffect(activity) {
    onDispose {
      if (!activity.isChangingConfigurations) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      }
    }
  }
}

internal fun requestedOrientationFor(screen: GameScreen): Int = when (screen) {
  GameScreen.KLONDIKE,
  GameScreen.SPIDER,
  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

  else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

@Composable
private fun GameListScreen(
  modifier: Modifier,
  onOpenSudoku: () -> Unit,
  onOpen2048: () -> Unit,
  onOpenNonogram: () -> Unit,
  onOpenMinesweeper: () -> Unit,
  onOpenKlondike: () -> Unit,
  onOpenSpider: () -> Unit,
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("一人用ゲーム", style = MaterialTheme.typography.titleLarge)
    Text("端末だけで遊べるゲームをまとめています。", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    GameTextCard("2048", "2048", "同じ数字を重ねて2048を目指すスライドパズル", onOpen2048)
    GameTextCard("▦", "ノノグラム", "縦横の数字を手掛かりにマスを塗る5×5ピクロス", onOpenNonogram)
    GameTextCard("✹", "マインスイーパー", "数字を手掛かりに地雷を避けて盤面を開くパズル", onOpenMinesweeper)
    GameTextCard("♠", "クロンダイク", "52枚のトランプを4組の組札へ揃える定番ソリティア", onOpenKlondike)
    GameTextCard(
      "🕷",
      "スパイダーソリティア",
      "104枚を並べ替えて同一スートのKからAを8組完成させるソリティア",
      onOpenSpider,
    )
  }
}

@Composable
private fun GameTextCard(icon: String, title: String, description: String, onClick: () -> Unit) {
  Card(onClick = onClick) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        icon,
        style = if (icon.length > 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineMedium,
        modifier = Modifier.size(32.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
          description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
