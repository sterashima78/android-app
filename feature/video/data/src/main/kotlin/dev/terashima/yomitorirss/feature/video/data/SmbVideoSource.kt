package dev.terashima.yomitorirss.feature.video.data

import android.net.Uri
import dev.terashima.yomitorirss.feature.video.VideoSource
import java.security.MessageDigest
import java.util.Locale

internal data class SmbVideoLocation(
  val serverId: String,
  val path: String,
)

internal fun smbVideoSourceId(serverId: String, path: String): String = Uri.Builder()
  .scheme("mosaic-smb-video")
  .authority("file")
  .appendQueryParameter("serverId", serverId)
  .appendQueryParameter("path", path)
  .build()
  .toString()

internal fun parseSmbVideoSourceId(sourceId: String): SmbVideoLocation {
  val uri = Uri.parse(sourceId)
  require(uri.scheme == "mosaic-smb-video" && uri.host == "file") { "SMB動画IDが不正です" }
  val serverId = uri.getQueryParameter("serverId")?.takeIf(String::isNotBlank)
    ?: error("SMB動画のserverIdがありません")
  val path = uri.getQueryParameter("path")?.takeIf(String::isNotBlank)
    ?: error("SMB動画のpathがありません")
  return SmbVideoLocation(serverId, path)
}

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
