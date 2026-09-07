package dev.terashima.yomitorirss.feature.audio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.audio.AudioPlaybackState
import dev.terashima.yomitorirss.feature.audio.AudioPreparationStatus
import kotlin.math.roundToInt

@Composable
fun AudioPlayerControls(
  state: AudioPlaybackState,
  onTogglePlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onSeekBack: () -> Unit,
  onSeekForward: () -> Unit,
  onSetSpeed: (Float) -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (state.preparationStatus == AudioPreparationStatus.IDLE) return

  Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      when (state.preparationStatus) {
        AudioPreparationStatus.PREPARING -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator()
            Column(modifier = Modifier.weight(1f)) {
              Text("音声を準備中", style = MaterialTheme.typography.titleSmall)
              Text(
                state.message ?: "${state.preparedCount}/${state.totalCount}",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            TextButton(onClick = onStop) { Text("停止") }
          }
        }

        AudioPreparationStatus.FAILED -> {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              state.message ?: "音声再生を開始できませんでした",
              modifier = Modifier.weight(1f),
              color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onStop) { Text("閉じる") }
          }
        }

        AudioPreparationStatus.READY -> {
          Text(
            state.currentItem?.title ?: "要約を再生中",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          state.currentItem?.source?.takeIf(String::isNotBlank)?.let { source ->
            Text(
              source,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          val progress = if (state.durationMs > 0L) {
            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
          } else {
            0f
          }
          LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
          Text(
            "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}  ・  ${state.currentIndex + 1}/${state.items.size}",
            style = MaterialTheme.typography.labelSmall,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(onClick = onPrevious) { Text("前") }
            TextButton(onClick = onSeekBack) { Text("-15秒") }
            TextButton(onClick = onTogglePlayPause) { Text(if (state.isPlaying) "一時停止" else "再生") }
            TextButton(onClick = onSeekForward) { Text("+30秒") }
            TextButton(onClick = onNext) { Text("次") }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
              listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                TextButton(onClick = { onSetSpeed(speed) }) {
                  val selected = (state.playbackSpeed * 100).roundToInt() == (speed * 100).roundToInt()
                  Text(if (selected) "[${formatSpeed(speed)}]" else formatSpeed(speed))
                }
              }
            }
            TextButton(onClick = onStop) { Text("終了") }
          }

          state.message?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall)
          }
        }

        AudioPreparationStatus.IDLE -> Unit
      }
    }
  }
}

private fun formatDuration(milliseconds: Long): String {
  val seconds = (milliseconds.coerceAtLeast(0L) / 1_000L).toInt()
  val minutes = seconds / 60
  return "%d:%02d".format(minutes, seconds % 60)
}

private fun formatSpeed(speed: Float): String = when (speed) {
  0.75f -> "0.75x"
  1f -> "1x"
  1.25f -> "1.25x"
  1.5f -> "1.5x"
  2f -> "2x"
  else -> "${speed}x"
}
