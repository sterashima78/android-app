package dev.terashima.yomitorirss.feature.video

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Source identity already persisted for an SMB-backed video item. */
data class VideoSmbFileIdentity(
  val serverId: String,
  val share: String?,
  val path: String,
)

/** Transient browser location derived from an SMB item and its configured sync root. */
data class VideoSmbBrowserPath(
  val sourceId: String,
  val rootName: String,
  val directories: List<String>,
)

fun parseVideoSmbSourceId(sourceId: String): VideoSmbFileIdentity? {
  val uri = runCatching { URI(sourceId) }.getOrNull() ?: return null
  if (uri.scheme != "mosaic-smb-video" || uri.host != "file") return null
  val serverId = queryParameter(uri.rawQuery, "serverId")?.takeIf(String::isNotBlank) ?: return null
  val share = queryParameter(uri.rawQuery, "share")?.takeIf(String::isNotBlank)
  val path = queryParameter(uri.rawQuery, "path")?.takeIf(String::isNotBlank) ?: return null
  return VideoSmbFileIdentity(serverId, share, path)
}

fun isVideoSmbPathWithinRoot(path: String, rootPath: String): Boolean {
  val pathSegments = videoSmbPathSegments(path)
  val rootSegments = videoSmbPathSegments(rootPath)
  if (rootSegments.size > pathSegments.size) return false
  return rootSegments.indices.all { index -> pathSegments[index] == rootSegments[index] }
}

fun VideoItem.smbBrowserPath(sources: List<VideoSmbSource>): VideoSmbBrowserPath? {
  if (source != VideoSource.SMB) return null
  val identity = parseVideoSmbSourceId(sourceId) ?: return null
  return runCatching {
    val source = sources
      .asSequence()
      .filter { it.serverId == identity.serverId }
      .filter { identity.share == null || it.share == identity.share }
      .filter { isVideoSmbPathWithinRoot(identity.path, it.rootPath) }
      .maxByOrNull { videoSmbPathSegments(it.rootPath).size }
      ?: return null

    val pathSegments = videoSmbPathSegments(identity.path)
    val rootSegments = videoSmbPathSegments(source.rootPath)
    val relative = pathSegments.drop(rootSegments.size)
    if (relative.isEmpty()) return null
    VideoSmbBrowserPath(
      sourceId = source.id,
      rootName = rootSegments.lastOrNull() ?: source.share,
      directories = relative.dropLast(1),
    )
  }.getOrNull()
}

private fun videoSmbPathSegments(path: String): List<String> = path
  .replace('/', '\\')
  .split('\\')
  .filter { it.isNotBlank() && it != "." }
  .also { require(".." !in it) { "SMB動画のパスに .. は使用できません" } }

private fun queryParameter(rawQuery: String?, name: String): String? = rawQuery
  ?.split('&')
  ?.firstOrNull { it.substringBefore('=') == name }
  ?.substringAfter('=', "")
  ?.let { encoded ->
    runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }.getOrNull()
  }
