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
import androidx.compose.material3.OutlinedButton
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
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 8.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text("スパイダーソリティア", style = MaterialTheme.typography.headlineSmall)
        Text(
          "手数: ${state.moves} ・ 完成: ${state.completedRuns.size}/8",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(onClick = onBack) { Text("ゲーム一覧") }
    }

    DifficultySelector(
      selected = state.difficulty,
      onSelect = onNewGame,
    )

    if (state.isWon) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("クリアしました", style = MaterialTheme.typography.titleMedium)
          Text("8組のKからAまでの列を完成させました。")
          Button(onClick = { onNewGame(state.difficulty) }) { Text("同じ難易度でもう一度") }
        }
      }
    }

    CompletedRuns(state.completedRuns)

    SpiderTableauBoard(
      state = state,
      onSelectTableau = onSelectTableau,
      onMoveSelectedToTableau = onMoveSelectedToTableau,
    )

    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Button(
        onClick = onDealStock,
        enabled = state.canDealStock,
      ) {
        Text("山札を配る（残り ${state.stock.size / 10} 回）")
      }
      if (state.stock.isNotEmpty() && state.tableau.any { it.isEmpty() }) {
        Text(
          "空いている列を埋めると山札を配れます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Text(
      "カードをタップして選択し、移動先の列をタップします。同一スートで数字が連続する列だけをまとめて移動できます。移動先はスートに関係なく1つ大きいカード、または空列です。同一スートのKからAが揃うと自動で回収されます。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
  }
}

@Composable
private fun DifficultySelector(
  selected: SpiderDifficulty,
  onSelect: (SpiderDifficulty) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      "難易度（変更すると新しいゲームを開始）",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      SpiderDifficulty.entries.forEach { difficulty ->
        if (difficulty == selected) {
          Button(
            onClick = { onSelect(difficulty) },
            modifier = Modifier.weight(1f),
          ) { Text(difficulty.label()) }
        } else {
          OutlinedButton(
            onClick = { onSelect(difficulty) },
            modifier = Modifier.weight(1f),
          ) { Text(difficulty.label()) }
        }
      }
    }
  }
}

@Composable
private fun CompletedRuns(completedRuns: List<CardSuit>) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    repeat(8) { index ->
      val suit = completedRuns.getOrNull(index)
      Box(
        modifier = Modifier
          .weight(1f)
          .height(28.dp)
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = suit?.symbol() ?: "—",
          style = MaterialTheme.typography.labelLarge,
          color = when {
            suit == null -> MaterialTheme.colorScheme.onSurfaceVariant
            suit.isRed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
          },
        )
      }
    }
  }
}

@Composable
private fun SpiderTableauBoard(
  state: SpiderGameState,
  onSelectTableau: (Int, Int) -> Unit,
  onMoveSelectedToTableau: (Int) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(470.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    state.tableau.forEachIndexed { pileIndex, pile ->
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight(),
      ) {
        if (pile.isEmpty()) {
          SpiderEmptySlot(
            modifier = Modifier.fillMaxWidth(),
            description = "空の場札 ${pileIndex + 1}列目",
            onClick = if (state.selection != null) {
              { onMoveSelectedToTableau(pileIndex) }
            } else {
              null
            },
          )
        } else {
          val step = when {
            pile.size >= 24 -> 13
            pile.size >= 20 -> 15
            pile.size >= 16 -> 18
            else -> 21
          }
          pile.forEachIndexed { cardIndex, tableauCard ->
            val selected = state.selection?.let { selection ->
              selection.pileIndex == pileIndex && cardIndex >= selection.cardIndex
            } == true
            val cardModifier = Modifier
              .fillMaxWidth()
              .offset(y = (cardIndex * step).dp)

            if (tableauCard.faceUp) {
              SpiderFaceCard(
                card = tableauCard.card,
                selected = selected,
                modifier = cardModifier,
                onClick = {
                  val current = state.selection
                  when {
                    current == null -> onSelectTableau(pileIndex, cardIndex)
                    current.pileIndex == pileIndex -> onSelectTableau(pileIndex, cardIndex)
                    else -> onMoveSelectedToTableau(pileIndex)
                  }
                },
              )
            } else {
              SpiderBackCard(
                modifier = cardModifier,
                description = "伏せ札 ${pileIndex + 1}列目",
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SpiderFaceCard(
  card: PlayingCard,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(4.dp)
  val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
  val borderWidth = if (selected) 2.dp else 1.dp
  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .clip(shape)
      .background(MaterialTheme.colorScheme.surface)
      .border(borderWidth, borderColor, shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 1.dp, vertical = 2.dp)
      .semantics { contentDescription = card.description() },
  ) {
    Text(
      text = "${card.rankLabel()}${card.suit.symbol()}",
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = if (card.suit.isRed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
    )
  }
}

@Composable
private fun SpiderBackCard(
  description: String,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .clip(RoundedCornerShape(4.dp))
      .background(MaterialTheme.colorScheme.primaryContainer)
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      "◆",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun SpiderEmptySlot(
  description: String,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(4.dp)
  val clickable = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .clip(shape)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
      .then(clickable)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      "＋",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun SpiderDifficulty.label(): String = when (this) {
  SpiderDifficulty.ONE_SUIT -> "1スート"
  SpiderDifficulty.TWO_SUITS -> "2スート"
  SpiderDifficulty.FOUR_SUITS -> "4スート"
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
