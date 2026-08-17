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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun KlondikeRoute(
  modifier: Modifier = Modifier,
  viewModel: KlondikeViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.state.collectAsState()
  KlondikeScreen(
    modifier = modifier,
    state = state,
    onBack = onBack,
    onNewGame = viewModel::newGame,
    onDrawStock = viewModel::drawStock,
    onSelectWaste = viewModel::selectWaste,
    onSelectFoundation = viewModel::selectFoundation,
    onSelectTableau = viewModel::selectTableau,
    onFlipTableauTop = viewModel::flipTableauTop,
    onMoveSelectedToTableau = viewModel::moveSelectedToTableau,
    onMoveSelectedToFoundation = viewModel::moveSelectedToFoundation,
  )
}

@Composable
private fun KlondikeScreen(
  state: KlondikeGameState,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
  onDrawStock: () -> Unit,
  onSelectWaste: () -> Unit,
  onSelectFoundation: (CardSuit) -> Unit,
  onSelectTableau: (Int, Int) -> Unit,
  onFlipTableauTop: (Int) -> Unit,
  onMoveSelectedToTableau: (Int) -> Unit,
  onMoveSelectedToFoundation: (CardSuit) -> Unit,
  modifier: Modifier = Modifier,
) {
  val validTableauTargets = state.validTableauTargets()
  val validFoundationTargets = state.validFoundationTargets()

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      KlondikeHeader(
        moves = state.moves,
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
            .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          KlondikeTopRow(
            state = state,
            validFoundationTargets = validFoundationTargets,
            onDrawStock = onDrawStock,
            onSelectWaste = onSelectWaste,
            onSelectFoundation = onSelectFoundation,
            onMoveSelectedToFoundation = onMoveSelectedToFoundation,
          )

          KlondikeSelectionStatus(
            state = state,
            validTableauTargets = validTableauTargets,
            validFoundationTargets = validFoundationTargets,
            onMoveSelectedToFoundation = onMoveSelectedToFoundation,
          )

          KlondikeTableauBoard(
            state = state,
            validTargets = validTableauTargets,
            onSelectTableau = onSelectTableau,
            onFlipTableauTop = onFlipTableauTop,
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
          Text("52枚すべてを組札へ移動しました。")
          Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth()) {
            Text("次のゲーム")
          }
        }
      }
    }
  }
}

@Composable
private fun KlondikeHeader(
  moves: Int,
  onBack: () -> Unit,
  onNewGame: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 6.dp)) {
      Text("‹ ゲーム")
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        "クロンダイク",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        "手数 $moves",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TextButton(onClick = onNewGame, contentPadding = PaddingValues(horizontal = 6.dp)) {
      Text("新しいゲーム")
    }
  }
}

@Composable
private fun KlondikeTopRow(
  state: KlondikeGameState,
  validFoundationTargets: Set<CardSuit>,
  onDrawStock: () -> Unit,
  onSelectWaste: () -> Unit,
  onSelectFoundation: (CardSuit) -> Unit,
  onMoveSelectedToFoundation: (CardSuit) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Box(modifier = Modifier.weight(1f)) {
      if (state.stock.isNotEmpty()) {
        CardBackView(
          modifier = Modifier.fillMaxWidth(),
          description = "山札 ${state.stock.size}枚",
          onClick = onDrawStock,
        )
      } else {
        CardSlotView(
          modifier = Modifier.fillMaxWidth(),
          label = if (state.waste.isNotEmpty()) "↻" else "山",
          description = if (state.waste.isNotEmpty()) "捨て札を山札へ戻す" else "山札は空です",
          onClick = if (state.waste.isNotEmpty()) onDrawStock else null,
        )
      }
      if (state.stock.isNotEmpty()) {
        CardCountBadge(
          text = state.stock.size.toString(),
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }

    val waste = state.waste.lastOrNull()
    if (waste == null) {
      CardSlotView(
        modifier = Modifier.weight(1f),
        label = "捨",
        description = "捨て札",
      )
    } else {
      PlayingCardView(
        card = waste,
        emphasis = if (state.selection == KlondikeSelection.Waste) {
          CardEmphasis.SELECTED
        } else {
          CardEmphasis.NORMAL
        },
        modifier = Modifier.weight(1f),
        onClick = onSelectWaste,
      )
    }

    CardSuit.entries.forEach { suit ->
      val foundation = state.foundations[suit].orEmpty()
      val top = foundation.lastOrNull()
      val selected = state.selection == KlondikeSelection.Foundation(suit)
      val destination = suit in validFoundationTargets
      val click = {
        when {
          selected || state.selection == null -> onSelectFoundation(suit)
          destination -> onMoveSelectedToFoundation(suit)
          else -> onSelectFoundation(suit)
        }
      }

      if (top == null) {
        CardSlotView(
          modifier = Modifier.weight(1f),
          label = suit.symbol(),
          description = "${suit.displayName()}の組札",
          emphasis = if (destination) CardEmphasis.DESTINATION else CardEmphasis.NORMAL,
          onClick = if (destination) click else null,
        )
      } else {
        PlayingCardView(
          card = top,
          emphasis = when {
            selected -> CardEmphasis.SELECTED
            destination -> CardEmphasis.DESTINATION
            else -> CardEmphasis.NORMAL
          },
          modifier = Modifier.weight(1f),
          onClick = click,
        )
      }
    }
  }
}

@Composable
private fun KlondikeSelectionStatus(
  state: KlondikeGameState,
  validTableauTargets: Set<Int>,
  validFoundationTargets: Set<CardSuit>,
  onMoveSelectedToFoundation: (CardSuit) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    val message = when {
      state.selection == null -> "カードを選ぶと移動可能先が光ります"
      validFoundationTargets.isNotEmpty() -> "${state.selectedCardCount()}枚選択 · 組札または光っている列へ移動"
      validTableauTargets.isNotEmpty() -> "${state.selectedCardCount()}枚選択 · 光っている列へ移動"
      else -> "${state.selectedCardCount()}枚選択 · 現在は移動先がありません"
    }
    Text(
      text = message,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White.copy(alpha = 0.78f),
      modifier = Modifier.weight(1f),
    )
    validFoundationTargets.singleOrNull()?.let { suit ->
      TextButton(
        onClick = { onMoveSelectedToFoundation(suit) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
      ) {
        Text("組札へ", color = Color.White)
      }
    }
  }
}

@Composable
private fun KlondikeTableauBoard(
  state: KlondikeGameState,
  validTargets: Set<Int>,
  onSelectTableau: (Int, Int) -> Unit,
  onFlipTableauTop: (Int) -> Unit,
  onMoveSelectedToTableau: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    state.tableau.forEachIndexed { pileIndex, pile ->
      KlondikeTableauPile(
        pile = pile,
        pileIndex = pileIndex,
        selection = state.selection,
        isDestination = pileIndex in validTargets,
        onSelectTableau = onSelectTableau,
        onFlipTableauTop = onFlipTableauTop,
        onMoveSelectedToTableau = onMoveSelectedToTableau,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
      )
    }
  }
}

@Composable
private fun KlondikeTableauPile(
  pile: List<TableauCard>,
  pileIndex: Int,
  selection: KlondikeSelection?,
  isDestination: Boolean,
  onSelectTableau: (Int, Int) -> Unit,
  onFlipTableauTop: (Int) -> Unit,
  onMoveSelectedToTableau: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(
    modifier = modifier
      .then(
        if (isDestination) {
          Modifier
            .border(2.dp, Color(0xFF80CBC4), RoundedCornerShape(9.dp))
            .background(Color(0xFF80CBC4).copy(alpha = 0.08f), RoundedCornerShape(9.dp))
            .clickable { onMoveSelectedToTableau(pileIndex) }
        } else {
          Modifier
        },
      )
      .padding(2.dp),
  ) {
    if (pile.isEmpty()) {
      CardSlotView(
        modifier = Modifier.fillMaxWidth(),
        label = "K",
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
        .coerceIn(13.dp, 29.dp)
    }

    pile.forEachIndexed { cardIndex, tableauCard ->
      val selected = selection is KlondikeSelection.Tableau &&
        selection.pileIndex == pileIndex &&
        cardIndex >= selection.cardIndex
      val isTop = cardIndex == pile.lastIndex
      val cardModifier = Modifier
        .fillMaxWidth()
        .offset(y = (step.value * cardIndex).dp)

      if (tableauCard.faceUp) {
        PlayingCardView(
          card = tableauCard.card,
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
          onClick = if (isTop && selection == null) {
            { onFlipTableauTop(pileIndex) }
          } else {
            null
          },
        )
      }
    }
  }
}

@Composable
private fun CardCountBadge(
  text: String,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .padding(3.dp)
      .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
      .padding(horizontal = 5.dp, vertical = 1.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = Color.White,
      fontWeight = FontWeight.Bold,
    )
  }
}
