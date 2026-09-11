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

private const val PODCAST_TRANSITION_DISPLAY_PREFIX = "続いて。"

internal fun podcastDisplayTitle(title: String): String =
  title.removePrefix(PODCAST_TRANSITION_DISPLAY_PREFIX)

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
  onSelectChapter: (Int) -> Unit,
  onStop: () -> Unit,
  onDismiss: () -> Unit,
) {
  val chapters = episode.playbackChapters()
  val hasMappedChapters = chapters.isNotEmpty() && chapters.all { it.article != null }
  val uriHandler = LocalUriHandler.current
  val displayAudioState = audioState.copy(
    items = audioState.items.map { item ->
      item.copy(title = podcastDisplayTitle(item.title))
    },
  )
  val displayItems = if (hasMappedChapters) {
    chapters.map { chapter ->
      val contentId = podcastChapterContentId(episode.id, chapter.number)
      PodcastArticleDisplayItem(
        label = "チャプター ${chapter.number}",
        article = requireNotNull(chapter.article),
        active = audioState.currentItem?.contentId == contentId,
        chapterNumber = chapter.number,
        available = audioState.items.any { it.contentId == contentId },
      )
    }
  } else {
    episode.articles.mapIndexed { index, article ->
      PodcastArticleDisplayItem(
        label = "記事 ${index + 1}",
        article = article,
        active = false,
        chapterNumber = null,
        available = false,
      )
    }
  }

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
            state = displayAudioState,
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

            displayItems.forEach { item ->
              val article = item.article
              Surface(
                onClick = {
                  item.chapterNumber?.let(onSelectChapter)
                },
                enabled = item.chapterNumber != null && item.available,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = if (item.active) 6.dp else 1.dp,
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    item.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (item.active) FontWeight.Bold else FontWeight.Normal,
                  )
                  Text(
                    podcastDisplayTitle(article.title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.active) FontWeight.Bold else FontWeight.Normal,
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

private data class PodcastArticleDisplayItem(
  val label: String,
  val article: PodcastEpisodeArticle,
  val active: Boolean,
  val chapterNumber: Int?,
  val available: Boolean,
)
