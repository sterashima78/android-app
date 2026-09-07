package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.video.VideoSource
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal data class SmbVideoLocation(
  val serverId: String,
  val share: String?,
  val path: String,
)

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
  val uri = runCatching { URI(sourceId) }.getOrNull()
    ?: error("SMB動画IDが不正です")
  require(uri.scheme == "mosaic-smb-video" && uri.host == "file") { "SMB動画IDが不正です" }
  val serverId = queryParameter(uri.rawQuery, "serverId")?.takeIf(String::isNotBlank)
    ?: error("SMB動画のserverIdがありません")
  val share = queryParameter(uri.rawQuery, "share")?.takeIf(String::isNotBlank)
  val path = queryParameter(uri.rawQuery, "path")?.takeIf(String::isNotBlank)
    ?: error("SMB動画のpathがありません")
  return SmbVideoLocation(serverId, share, path)
}

internal fun isSmbVideoPathWithinRoot(path: String, rootPath: String): Boolean {
  val normalizedPath = normalizeSmbVideoPath(path)
  val normalizedRoot = normalizeSmbVideoPath(rootPath)
  return normalizedRoot.isEmpty() || normalizedPath == normalizedRoot ||
    normalizedPath.startsWith("$normalizedRoot\\")
}

private fun normalizeSmbVideoPath(path: String): String = path
  .replace('/', '\\')
  .split('\\')
  .filter { it.isNotBlank() && it != "." }
  .also { require(".." !in it) { "SMB動画のパスに .. は使用できません" } }
  .joinToString("\\")

private fun encodeSmbVideoIdPart(value: String): String =
  URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private fun queryParameter(rawQuery: String?, name: String): String? = rawQuery
  ?.split('&')
  ?.firstOrNull { it.substringBefore('=') == name }
  ?.substringAfter('=', "")
  ?.let { encoded ->
    runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }.getOrNull()
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
