package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.library.SmbMediaLocation
import dev.terashima.yomitorirss.feature.video.VideoByteSource
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackResolver
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule

class DefaultVideoPlaybackResolver(
  private val smbMediaFileAccess: SmbMediaFileAccess,
  private val smbSources: () -> List<VideoSmbSource>,
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
    val configured = smbSources()
      .filter { source -> source.serverId == location.serverId }
      .filter { source -> location.share == null || source.share == location.share }
      .filter { source -> isSmbVideoPathWithinRoot(location.path, source.rootPath) }
      .maxByOrNull { source -> source.rootPath.length }
      ?: error("このSMB動画の同期場所設定がありません")
    val handle = smbMediaFileAccess.openMediaFile(
      location = SmbMediaLocation(configured.serverId, configured.share, configured.rootPath),
      path = location.path,
    )
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
      ?.let { VideoPlaybackTarget.Stream(it, custom.mimeType) }
      ?: VideoPlaybackTarget.WebPage(pageUrl)
  }
}
