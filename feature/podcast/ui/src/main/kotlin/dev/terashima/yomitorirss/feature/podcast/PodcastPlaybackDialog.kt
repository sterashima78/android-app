package dev.terashima.yomitorirss.feature.podcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.terashima.yomitorirss.feature.audio.AudioPlaybackState
import dev.terashima.yomitorirss.feature.audio.ui.AudioPlayerControls

@Composable
fun PodcastPlaybackDialog(
  episode: PodcastEpisode,
  audioState: AudioPlaybackState,
  onTogglePlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onSeekBack: () -> Unit,
  onSeekForward: () -> Unit,
  onSetSpeed: (Float) -> Unit,
  onStop: () -> Unit,
  onDismiss: () -> Unit,
) {
  val chapters = episode.playbackChapters()
  val hasMappedChapters = chapters.isNotEmpty() && chapters.all { it.article != null }
  val uriHandler = LocalUriHandler.current

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      Column(Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              episode.title,
              style = MaterialTheme.typography.titleLarge,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              "${episode.articles.size}記事",
              style = MaterialTheme.typography.bodySmall,
            )
          }
          TextButton(onClick = onDismiss) { Text("閉じる") }
        }
        HorizontalDivider()

        Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          AudioPlayerControls(
            state = audioState,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onSetSpeed = onSetSpeed,
            onStop = onStop,
          )

          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              if (hasMappedChapters) "チャプター" else "関連記事",
              style = MaterialTheme.typography.titleMedium,
            )
            if (!hasMappedChapters) {
              Text(
                "以前に生成したエピソードなど、チャプター情報がない原稿は全文を連続再生します。",
                style = MaterialTheme.typography.bodySmall,
              )
            }

            episode.articles.forEachIndexed { index, article ->
              val chapterNumber = index + 1
              val active = hasMappedChapters &&
                audioState.currentItem?.contentId == podcastChapterContentId(episode.id, chapterNumber)
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = if (active) 6.dp else 1.dp,
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    if (hasMappedChapters) "チャプター $chapterNumber" else "記事 $chapterNumber",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                  )
                  Text(
                    article.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                  )
                  article.sourceTitle?.takeIf(String::isNotBlank)?.let { source ->
                    Text(source, style = MaterialTheme.typography.bodySmall)
                  }
                  article.articleUrl?.takeIf(String::isNotBlank)?.let { url ->
                    OutlinedButton(onClick = { uriHandler.openUri(url) }) {
                      Text("記事を開く")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
