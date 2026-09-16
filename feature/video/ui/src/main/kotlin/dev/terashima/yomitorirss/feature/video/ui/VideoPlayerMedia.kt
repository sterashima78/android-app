package dev.terashima.yomitorirss.feature.video.ui

import android.content.Context
import android.webkit.WebSettings
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget

internal data class VideoPlayerMedia(
  val player: ExoPlayer,
  val smbDataSourceFactory: SmbVideoDataSource.Factory?,
)

internal fun createVideoPlayerMedia(
  context: Context,
  target: VideoPlaybackTarget,
  resumePositionMs: Long,
  byteSourceFactory: VideoByteSourceFactory,
): VideoPlayerMedia {
  require(target is VideoPlaybackTarget.Stream || target is VideoPlaybackTarget.Smb)

  val smbDataSourceFactory = if (target is VideoPlaybackTarget.Smb) {
    SmbVideoDataSource.Factory(byteSourceFactory)
  } else {
    null
  }

  val builder = ExoPlayer.Builder(context)
  when (target) {
    is VideoPlaybackTarget.Stream -> {
      val userAgent = WebSettings.getDefaultUserAgent(context)
      val requestProperties = webStreamRequestProperties(target)
      val dataSourceFactory = target.cookieProvider?.let { cookieProvider ->
        WebVideoHttpDataSource.Factory(
          userAgent = userAgent,
          defaultRequestProperties = requestProperties,
          cookieProvider = cookieProvider,
        )
      } ?: DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(requestProperties)
      builder.setMediaSourceFactory(
        DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory),
      )
    }
    is VideoPlaybackTarget.Smb -> builder.setMediaSourceFactory(
      DefaultMediaSourceFactory(requireNotNull(smbDataSourceFactory)),
    )
    is VideoPlaybackTarget.WebPage -> error("WebページはMedia3で再生しません")
  }

  val mediaItem = when (target) {
    is VideoPlaybackTarget.Stream -> MediaItem.Builder()
      .setUri(target.url)
      .apply { target.mimeType?.let(::setMimeType) }
      .build()
    is VideoPlaybackTarget.Smb -> MediaItem.Builder()
      .setUri(SmbVideoDataSource.mediaUri(target.sourceId))
      .apply { target.mimeType?.let(::setMimeType) }
      .build()
    is VideoPlaybackTarget.WebPage -> error("WebページはMedia3で再生しません")
  }

  val player = builder.build().apply {
    setMediaItem(mediaItem)
    prepare()
    if (resumePositionMs > 0L) seekTo(resumePositionMs)
    playWhenReady = true
  }

  return VideoPlayerMedia(
    player = player,
    smbDataSourceFactory = smbDataSourceFactory,
  )
}
