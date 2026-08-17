package dev.terashima.yomitorirss.feature.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class CardEmphasis {
  NORMAL,
  SELECTED,
  DESTINATION,
}

private val feltTop = Color(0xFF174E3B)
private val feltBottom = Color(0xFF0B2F25)
private val cardFace = Color(0xFFFFFCF3)
private val cardBlack = Color(0xFF202124)
private val cardRed = Color(0xFFB3261E)
private val cardBack = Color(0xFF244D7A)
private val cardBackInk = Color(0xFF8FB3D9)
private val cardOutline = Color(0xFFCBC6B9)
private val selectedOutline = Color(0xFFFFC857)
private val destinationOutline = Color(0xFF80CBC4)

@Composable
internal fun GameTableSurface(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(18.dp))
      .background(Brush.verticalGradient(listOf(feltTop, feltBottom)))
      .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp)),
    content = content,
  )
}

@Composable
internal fun PlayingCardView(
  card: PlayingCard,
  emphasis: CardEmphasis = CardEmphasis.NORMAL,
  compact: Boolean = false,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(if (compact) 5.dp else 8.dp)
  val borderTarget = when (emphasis) {
    CardEmphasis.SELECTED -> selectedOutline
    CardEmphasis.DESTINATION -> destinationOutline
    CardEmphasis.NORMAL -> cardOutline
  }
  val borderColor by animateColorAsState(borderTarget, label = "cardBorder")
  val elevation by animateDpAsState(
    if (emphasis == CardEmphasis.SELECTED) 8.dp else 2.dp,
    label = "cardElevation",
  )
  val scale by animateFloatAsState(
    if (emphasis == CardEmphasis.SELECTED) 1.035f else 1f,
    label = "cardScale",
  )
  val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
  val ink = if (card.suit.isRed) cardRed else cardBlack
  val cornerSize = if (compact) 8.sp else 12.sp

  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .shadow(elevation, shape, clip = false)
      .clip(shape)
      .background(cardFace)
      .border(
        width = if (emphasis == CardEmphasis.NORMAL) 1.dp else 2.dp,
        color = borderColor,
        shape = shape,
      )
      .then(clickableModifier)
      .padding(if (compact) 2.dp else 4.dp)
      .semantics { contentDescription = card.description() },
  ) {
    Text(
      text = "${card.rankLabel()}${card.suit.symbol()}",
      color = ink,
      fontSize = cornerSize,
      lineHeight = cornerSize,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Start,
      maxLines = 1,
      modifier = Modifier.align(Alignment.TopStart),
    )
    if (!compact) {
      Text(
        text = card.suit.symbol(),
        color = ink.copy(alpha = 0.82f),
        fontSize = 20.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.Center),
      )
      Text(
        text = "${card.rankLabel()}${card.suit.symbol()}",
        color = ink,
        fontSize = cornerSize,
        lineHeight = cornerSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .graphicsLayer { rotationZ = 180f },
      )
    }
  }
}

@Composable
internal fun CardBackView(
  modifier: Modifier = Modifier,
  description: String,
  onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(7.dp)
  val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .shadow(2.dp, shape, clip = false)
      .clip(shape)
      .background(cardBack)
      .border(1.dp, Color.White.copy(alpha = 0.38f), shape)
      .drawBehind {
        val step = 12.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
          drawLine(
            color = cardBackInk.copy(alpha = 0.35f),
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x + size.height, size.height),
            strokeWidth = 1.dp.toPx(),
          )
          x += step
        }
      }
      .then(clickableModifier)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .padding(5.dp)
        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(4.dp)),
    )
  }
}

@Composable
internal fun CardSlotView(
  label: String,
  description: String,
  emphasis: CardEmphasis = CardEmphasis.NORMAL,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(7.dp)
  val borderColor by animateColorAsState(
    when (emphasis) {
      CardEmphasis.DESTINATION -> destinationOutline
      CardEmphasis.SELECTED -> selectedOutline
      CardEmphasis.NORMAL -> Color.White.copy(alpha = 0.30f)
    },
    label = "slotBorder",
  )
  val clickableModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
  Box(
    modifier = modifier
      .aspectRatio(0.68f)
      .clip(shape)
      .background(Color.Black.copy(alpha = 0.10f))
      .border(if (emphasis == CardEmphasis.NORMAL) 1.dp else 2.dp, borderColor, shape)
      .then(clickableModifier)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      color = Color.White.copy(alpha = 0.62f),
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center,
    )
  }
}

internal fun PlayingCard.rankLabel(): String = when (rank) {
  1 -> "A"
  11 -> "J"
  12 -> "Q"
  13 -> "K"
  else -> rank.toString()
}

internal fun CardSuit.symbol(): String = when (this) {
  CardSuit.CLUBS -> "♣"
  CardSuit.DIAMONDS -> "♦"
  CardSuit.HEARTS -> "♥"
  CardSuit.SPADES -> "♠"
}

internal fun CardSuit.displayName(): String = when (this) {
  CardSuit.CLUBS -> "クラブ"
  CardSuit.DIAMONDS -> "ダイヤ"
  CardSuit.HEARTS -> "ハート"
  CardSuit.SPADES -> "スペード"
}

internal fun PlayingCard.description(): String = "${suit.displayName()}の${rankLabel()}"
