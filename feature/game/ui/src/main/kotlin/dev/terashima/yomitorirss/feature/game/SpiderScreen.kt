package dev.terashima.yomitorirss.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SpiderRoute(
  modifier: Modifier = Modifier,
  viewModel: SpiderViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.state.collectAsState()
  SpiderScreen(
    state = state,
    modifier = modifier,
    onBack = onBack,
    onNewGame = viewModel::newGame,
    onSelectTableau = viewModel::selectTableau,
    onMoveSelectedToTableau = viewModel::moveSelectedToTableau,
    onDealStock = viewModel::dealStock,
  )
}

@Composable
private fun SpiderScreen(
  state: SpiderGameState,
  onBack: () -> Unit,
  onNewGame: (SpiderDifficulty) -> Unit,
  onSelectTableau: (Int, Int) -> Unit,
  onMoveSelectedToTableau: (Int) -> Unit,
  onDealStock: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val validTargets = state.validTableauTargets()

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 6.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      SpiderHeader(
        state = state,
        onBack = onBack,
        onNewGame = onNewGame,
      )

      GameTableSurface(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(7.dp),
          verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
          ) {
            SpiderCompletedRuns(
              completedRuns = state.completedRuns,
              modifier = Modifier.weight(1f),
            )
            SpiderStockControl(
              state = state,
              onDealStock = onDealStock,
              modifier = Modifier.width(52.dp),
            )
          }

          SpiderSelectionStatus(
            state = state,
            validTargets = validTargets,
          )

          SpiderTableauBoard(
            state = state,
            validTargets = validTargets,
            onSelectTableau = onSelectTableau,
            onMoveSelectedToTableau = onMoveSelectedToTableau,
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
          )
        }
      }
    }

    AnimatedVisibility(
      visible = state.isWon,
      modifier = Modifier.align(Alignment.Center),
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Card(modifier = Modifier.widthIn(max = 320.dp).padding(20.dp)) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("クリア", style = MaterialTheme.typography.headlineSmall)
          Text("8組のKからAまでの列を完成させました。")
          Button(
            onClick = { onNewGame(state.difficulty) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("同じ難易度でもう一度")
          }
        }
      }
    }
  }
}

@Composable
private fun SpiderHeader(
  state: SpiderGameState,
  onBack: () -> Unit,
  onNewGame: (SpiderDifficulty) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 5.dp)) {
      Text("‹ ゲーム")
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        "スパイダー",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        "手数 ${state.moves} · 完成 ${state.completedRuns.size}/8",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Box {
      TextButton(
        onClick = { menuOpen = true },
        contentPadding = PaddingValues(horizontal = 6.dp),
      ) {
        Text("${state.difficulty.label()} ▾")
      }
      DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
      ) {
        SpiderDifficulty.entries.forEach { difficulty ->
          DropdownMenuItem(
            text = { Text(difficulty.label()) },
            onClick = {
              menuOpen = false
              if (difficulty != state.difficulty) onNewGame(difficulty)
            },
          )
        }
      }
    }
    TextButton(
      onClick = { onNewGame(state.difficulty) },
      contentPadding = PaddingValues(horizontal = 5.dp),
    ) {
      Text("新規")
    }
  }
}

@Composable
private fun SpiderCompletedRuns(
  completedRuns: List<CardSuit>,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    repeat(8) { index ->
      val suit = completedRuns.getOrNull(index)
      Box(
        modifier = Modifier
          .weight(1f)
          .background(Color.Black.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
          .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(5.dp))
          .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = suit?.symbol() ?: "·",
          style = MaterialTheme.typography.labelMedium,
          color = when {
            suit == null -> Color.White.copy(alpha = 0.35f)
            suit.isRed -> Color(0xFFFF8A80)
            else -> Color.White
          },
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun SpiderStockControl(
  state: SpiderGameState,
  onDealStock: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    if (state.stock.isNotEmpty()) {
      CardBackView(
        modifier = Modifier.fillMaxWidth(),
        description = "山札を配る。残り ${state.stock.size / 10}回",
        onClick = if (state.canDealStock) onDealStock else null,
      )
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(2.dp)
          .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
          .padding(horizontal = 4.dp, vertical = 1.dp),
      ) {
        Text(
          text = "×${state.stock.size / 10}",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White,
          fontWeight = FontWeight.Bold,
        )
      }
    } else {
      CardSlotView(
        modifier = Modifier.fillMaxWidth(),
        label = "完",
        description = "山札は空です",
      )
    }
  }
}

@Composable
private fun SpiderSelectionStatus(
  state: SpiderGameState,
  validTargets: Set<Int>,
) {
  val message = when {
    state.selection != null && validTargets.isNotEmpty() ->
      "${state.selectedCardCount()}枚選択 · 光っている列へ移動"
    state.selection != null ->
      "${state.selectedCardCount()}枚選択 · 現在は移動先がありません"
    state.stock.isNotEmpty() && !state.canDealStock && state.tableau.any { it.isEmpty() } ->
      "空列を埋めると右上の山札を配れます"
    else ->
      "同一スートの連続列を選ぶと移動可能先が光ります"
  }

  Text(
    text = message,
    style = MaterialTheme.typography.labelSmall,
    color = Color.White.copy(alpha = 0.78f),
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun SpiderTableauBoard(
  state: SpiderGameState,
  validTargets: Set<Int>,
  onSelectTableau: (Int, Int) -> Unit,
  onMoveSelectedToTableau: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    state.tableau.forEachIndexed { pileIndex, pile ->
      SpiderTableauPile(
        pile = pile,
        pileIndex = pileIndex,
        selection = state.selection,
        isDestination = pileIndex in validTargets,
        onSelectTableau = onSelectTableau,
        onMoveSelectedToTableau = onMoveSelectedToTableau,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
      )
    }
  }
}

@Composable
private fun SpiderTableauPile(
  pile: List<SpiderTableauCard>,
  pileIndex: Int,
  selection: SpiderSelection?,
  isDestination: Boolean,
  onSelectTableau: (Int, Int) -> Unit,
  onMoveSelectedToTableau: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier = modifier
      .then(
        if (isDestination) {
          Modifier
            .border(2.dp, Color(0xFF80CBC4), RoundedCornerShape(7.dp))
            .background(Color(0xFF80CBC4).copy(alpha = 0.08f), RoundedCornerShape(7.dp))
            .clickable { onMoveSelectedToTableau(pileIndex) }
        } else {
          Modifier
        },
      )
      .padding(1.dp),
  ) {
    if (pile.isEmpty()) {
      CardSlotView(
        modifier = Modifier.fillMaxWidth(),
        label = "+",
        description = "空の場札 ${pileIndex + 1}列目",
        emphasis = if (isDestination) CardEmphasis.DESTINATION else CardEmphasis.NORMAL,
        onClick = if (isDestination) ({ onMoveSelectedToTableau(pileIndex) }) else null,
      )
      return@BoxWithConstraints
    }

    val cardHeight = maxWidth.value / 0.68f
    val step = if (pile.size <= 1) {
      0.dp
    } else {
      ((maxHeight.value - cardHeight).coerceAtLeast(0f) / (pile.size - 1))
        .dp
        .coerceIn(10.dp, 24.dp)
    }

    pile.forEachIndexed { cardIndex, tableauCard ->
      val selected = selection?.let { current ->
        current.pileIndex == pileIndex && cardIndex >= current.cardIndex
      } == true
      val cardModifier = Modifier
        .fillMaxWidth()
        .offset(y = (step.value * cardIndex).dp)

      if (tableauCard.faceUp) {
        PlayingCardView(
          card = tableauCard.card,
          compact = true,
          emphasis = if (selected) CardEmphasis.SELECTED else CardEmphasis.NORMAL,
          modifier = cardModifier,
          onClick = {
            if (selection != null && isDestination) {
              onMoveSelectedToTableau(pileIndex)
            } else {
              onSelectTableau(pileIndex, cardIndex)
            }
          },
        )
      } else {
        CardBackView(
          modifier = cardModifier,
          description = "伏せ札 ${pileIndex + 1}列目",
        )
      }
    }
  }
}

private fun SpiderDifficulty.label(): String = when (this) {
  SpiderDifficulty.ONE_SUIT -> "1スート"
  SpiderDifficulty.TWO_SUITS -> "2スート"
  SpiderDifficulty.FOUR_SUITS -> "4スート"
}
