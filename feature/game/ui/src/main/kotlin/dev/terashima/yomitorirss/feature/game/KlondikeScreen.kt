package dev.terashima.yomitorirss.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text("クロンダイク", style = MaterialTheme.typography.headlineSmall)
        Text(
          "手数: ${state.moves}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(onClick = onBack) { Text("ゲーム一覧") }
    }

    if (state.isWon) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("クリアしました", style = MaterialTheme.typography.titleMedium)
          Text("52枚すべてを組札へ移動しました。")
          Button(onClick = onNewGame) { Text("新しいゲーム") }
        }
      }
    }

    KlondikeTopRow(
      state = state,
      onDrawStock = onDrawStock,
      onSelectWaste = onSelectWaste,
      onSelectFoundation = onSelectFoundation,
      onMoveSelectedToFoundation = onMoveSelectedToFoundation,
    )

    TableauBoard(
      state = state,
      onSelectTableau = onSelectTableau,
      onFlipTableauTop = onFlipTableauTop,
      onMoveSelectedToTableau = onMoveSelectedToTableau,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Center,
    ) {
      Button(onClick = onNewGame) { Text("新しいゲーム") }
    }

    Text(
      "カードをタップして選択し、移動先の列または組札をタップします。場札は赤黒交互の降順、空列にはKのみ置けます。山札は1枚ずつめくり、空になったら捨て札を再利用できます。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun KlondikeTopRow(
  state: KlondikeGameState,
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
    EmptyOrBackCard(
      modifier = Modifier.weight(1f),
      hasCard = state.stock.isNotEmpty(),
      emptyLabel = if (state.waste.isNotEmpty()) "↻" else "山",
      description = if (state.stock.isNotEmpty()) "山札 ${state.stock.size}枚" else "捨て札を山札へ戻す",
      onClick = onDrawStock,
    )

    val waste = state.waste.lastOrNull()
    if (waste == null) {
      EmptyCardSlot(
        modifier = Modifier.weight(1f),
        label = "捨",
        description = "捨て札",
      )
    } else {
      FaceCard(
        card = waste,
        selected = state.selection == KlondikeSelection.Waste,
        modifier = Modifier.weight(1f),
        onClick = onSelectWaste,
      )
    }

    CardSuit.entries.forEach { suit ->
      val foundation = state.foundations[suit].orEmpty()
      val top = foundation.lastOrNull()
      val selected = state.selection == KlondikeSelection.Foundation(suit)
      val click = {
        if (state.selection == null || selected) {
          onSelectFoundation(suit)
        } else {
          onMoveSelectedToFoundation(suit)
        }
      }
      if (top == null) {
        EmptyCardSlot(
          modifier = Modifier.weight(1f),
          label = suit.symbol(),
          description = "${suit.displayName()}の組札",
          onClick = if (state.selection != null) click else null,
        )
      } else {
        FaceCard(
          card = top,
          selected = selected,
          modifier = Modifier.weight(1f),
          onClick = click,
        )
      }
    }
  }
}

@Composable
private fun TableauBoard(
  state: KlondikeGameState,
  onSelectTableau: (Int, Int) -> Unit,
  onFlipTableauTop: (Int) -> Unit,
  onMoveSelectedToTableau: (Int) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(410.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    state.tableau.forEachIndexed { pileIndex, pile ->
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
      ) {
        if (pile.isEmpty()) {
          EmptyCardSlot(
            modifier = Modifier.fillMaxWidth(),
            label = "K",
            description = "空の場札 ${pileIndex + 1}列目",
            onClick = if (state.selection != null) {
              { onMoveSelectedToTableau(pileIndex) }
            } else {
              null
            },
          )
        } else {
          pile.forEachIndexed { cardIndex, tableauCard ->
            val selected = state.selection == KlondikeSelection.Tableau(pileIndex, cardIndex)
            val isTop = cardIndex == pile.lastIndex
            val cardModifier = Modifier
              .fillMaxWidth()
              .offset(y = (cardIndex * 24).dp)

            if (!tableauCard.faceUp) {
              EmptyOrBackCard(
                modifier = cardModifier,
                hasCard = true,
                emptyLabel = "",
                description = "伏せ札 ${pileIndex + 1}列目",
                onClick = if (isTop && state.selection == null) {
                  { onFlipTableauTop(pileIndex) }
                } else {
                  {}
                },
              )
            } else {
              FaceCard(
                card = tableauCard.card,
                selected = selected,
                modifier = cardModifier,
                onClick = {
                  val current = state.selection
                  when {
                    current == null -> onSelectTableau(pileIndex, cardIndex)
                    current is KlondikeSelection.Tableau && current.pileIndex == pileIndex -> {
                      onSelectTableau(pileIndex, cardIndex)
                    }
                    else -> onMoveSelectedToTableau(pileIndex)
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FaceCard(
  card: PlayingCard,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(6.dp)
  val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
  val borderWidth = if (selected) 2.dp else 1.dp
  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surface)
      .border(borderWidth, borderColor, shape)
      .clickable(onClick = onClick)
      .padding(3.dp)
      .semantics { contentDescription = card.description() },
  ) {
    Text(
      text = "${card.rankLabel()}${card.suit.symbol()}",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = if (card.suit.isRed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
    )
    Text(
      text = card.suit.symbol(),
      style = MaterialTheme.typography.titleMedium,
      color = if (card.suit.isRed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.align(Alignment.Center),
    )
  }
}

@Composable
private fun EmptyOrBackCard(
  hasCard: Boolean,
  emptyLabel: String,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(6.dp)
  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .clip(shape)
      .background(
        if (hasCard) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
      )
      .border(1.dp, MaterialTheme.colorScheme.outline, shape)
      .clickable(onClick = onClick)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = if (hasCard) "◆" else emptyLabel,
      style = MaterialTheme.typography.titleMedium,
      color = if (hasCard) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun EmptyCardSlot(
  label: String,
  description: String,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(6.dp)
  val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .clip(shape)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
      .then(clickableModifier)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

private fun PlayingCard.rankLabel(): String = when (rank) {
  1 -> "A"
  11 -> "J"
  12 -> "Q"
  13 -> "K"
  else -> rank.toString()
}

private fun CardSuit.symbol(): String = when (this) {
  CardSuit.CLUBS -> "♣"
  CardSuit.DIAMONDS -> "♦"
  CardSuit.HEARTS -> "♥"
  CardSuit.SPADES -> "♠"
}

private fun CardSuit.displayName(): String = when (this) {
  CardSuit.CLUBS -> "クラブ"
  CardSuit.DIAMONDS -> "ダイヤ"
  CardSuit.HEARTS -> "ハート"
  CardSuit.SPADES -> "スペード"
}

private fun PlayingCard.description(): String = "${suit.displayName()}の${rankLabel()}"
