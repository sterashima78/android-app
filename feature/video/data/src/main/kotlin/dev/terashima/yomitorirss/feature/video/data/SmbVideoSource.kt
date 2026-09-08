package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.video.VideoSmbFileIdentity
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.isVideoSmbPathWithinRoot
import dev.terashima.yomitorirss.feature.video.parseVideoSmbSourceId
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal typealias SmbVideoLocation = VideoSmbFileIdentity

internal fun smbVideoSourceId(serverId: String, share: String, path: String): String {
  require(serverId.isNotBlank()) { "SMB動画のserverIdがありません" }
  require(share.isNotBlank()) { "SMB動画のshareがありません" }
  require(path.isNotBlank()) { "SMB動画のpathがありません" }
  return "mosaic-smb-video://file?serverId=${encodeSmbVideoIdPart(serverId)}" +
    "&share=${encodeSmbVideoIdPart(share)}&path=${encodeSmbVideoIdPart(path)}"
}

internal fun legacySmbVideoSourceId(serverId: String, path: String): String {
  require(serverId.isNotBlank()) { "SMB動画のserverIdがありません" }
  require(path.isNotBlank()) { "SMB動画のpathがありません" }
  return "mosaic-smb-video://file?serverId=${encodeSmbVideoIdPart(serverId)}" +
    "&path=${encodeSmbVideoIdPart(path)}"
}

internal fun parseSmbVideoSourceId(sourceId: String): SmbVideoLocation {
  return requireNotNull(parseVideoSmbSourceId(sourceId)) { "SMB動画IDが不正です" }
}

internal fun isSmbVideoPathWithinRoot(path: String, rootPath: String): Boolean {
  return isVideoSmbPathWithinRoot(path, rootPath)
}

private fun encodeSmbVideoIdPart(value: String): String =
  URLEncoder.encode(value, StandardCharsets.UTF_8.name())

internal fun stableVideoId(source: VideoSource, sourceId: String): String {
  val digest = MessageDigest.getInstance("SHA-256")
    .digest("${source.name}\n$sourceId".toByteArray(Charsets.UTF_8))
  return digest.joinToString("") { "%02x".format(it) }
}

internal fun videoMimeType(name: String): String? = when (
  name.substringAfterLast('.', "").lowercase(Locale.ROOT)
) {
  "mp4", "m4v" -> "video/mp4"
  "mkv" -> "video/x-matroska"
  "webm" -> "video/webm"
  "mov" -> "video/quicktime"
  else -> null
}

internal val SMB_VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4v", "mov")
