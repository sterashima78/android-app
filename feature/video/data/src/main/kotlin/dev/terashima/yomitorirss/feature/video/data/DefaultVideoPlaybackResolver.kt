package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackResolver
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule

class DefaultVideoPlaybackResolver(
  private val smbMediaFileAccess: SmbMediaFileAccess,
  private val rules: () -> List<WebVideoExtractorRule>,
  private val webExtractorClient: AndroidWebVideoExtractorClient,
) : VideoPlaybackResolver, VideoByteSourceFactory {
  override suspend fun resolve(item: VideoItem): VideoPlaybackTarget = when (item.source) {
    VideoSource.SMB -> VideoPlaybackTarget.Smb(
      sourceId = item.sourceId,
      length = item.sizeBytes ?: open(item.sourceId).use(VideoByteSource::length),
      mimeType = item.mimeType,
    )

    VideoSource.WEB -> resolveWeb(item)

    VideoSource.SERVICE -> item.pageUrl
      ?.let(VideoPlaybackTarget::WebPage)
      ?: error("この動画サービスの再生先がありません")
  }

  override fun open(sourceId: String): VideoByteSource {
    val location = parseSmbVideoSourceId(sourceId)
    val handle = smbMediaFileAccess.openMediaFile(location.serverId, location.path)
    return object : VideoByteSource {
      override val length: Long get() = handle.size

      override fun read(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
      ): Int = handle.read(position, buffer, offset, length)

      override fun close() = handle.close()
    }
  }

  private suspend fun resolveWeb(item: VideoItem): VideoPlaybackTarget {
    val pageUrl = item.pageUrl ?: item.sourceId
    val rule = findMatchingWebVideoExtractorRule(rules(), pageUrl)
    val custom = if (!rule?.playbackExtractorCode.isNullOrBlank()) {
      runCatching { webExtractorClient.extract(pageUrl, requireNotNull(rule)) }.getOrNull()
    } else {
      null
    }
    return custom?.streamUrl
      ?.takeIf(String::isNotBlank)
      ?.let {
        VideoPlaybackTarget.Stream(
          url = it,
          mimeType = custom.mimeType,
          referrerUrl = pageUrl,
        )
      }
      ?: VideoPlaybackTarget.WebPage(pageUrl)
  }
}
