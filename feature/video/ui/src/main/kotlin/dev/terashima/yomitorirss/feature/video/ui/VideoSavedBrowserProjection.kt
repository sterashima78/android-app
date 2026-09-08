package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoFolder
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.smbBrowserPath

internal sealed interface VideoSavedBrowserLocation {
  data object Root : VideoSavedBrowserLocation

  data class CustomFolder(
    val id: String,
    val name: String,
  ) : VideoSavedBrowserLocation

  data class SmbDirectory(
    val sourceId: String,
    val rootName: String,
    val path: List<String> = emptyList(),
  ) : VideoSavedBrowserLocation
}

internal data class VideoSavedDirectoryEntry(
  val key: String,
  val name: String,
  val location: VideoSavedBrowserLocation,
)

internal data class VideoSavedBreadcrumb(
  val label: String,
  val location: VideoSavedBrowserLocation,
)

internal data class VideoSavedBrowserContent(
  val directories: List<VideoSavedDirectoryEntry>,
  val videos: List<VideoItem>,
  val breadcrumbs: List<VideoSavedBreadcrumb>,
)

internal fun buildVideoSavedBrowserContent(
  items: List<VideoItem>,
  folders: List<VideoFolder>,
  smbSources: List<VideoSmbSource>,
  location: VideoSavedBrowserLocation,
  sourceFilter: VideoSource?,
): VideoSavedBrowserContent {
  val savedItems = items.filter { item ->
    item.isSaved && (sourceFilter == null || item.source == sourceFilter)
  }

  return when (location) {
    VideoSavedBrowserLocation.Root -> {
      val smbLocations = savedItems
        .asSequence()
        .filter { it.source == VideoSource.SMB }
        .mapNotNull { item -> item.smbBrowserPath(smbSources)?.let { path -> item to path } }
        .toList()
      val mappedSmbIds = smbLocations.mapTo(HashSet()) { it.first.id }
      val smbRoots = smbLocations
        .groupBy { it.second.sourceId }
        .mapNotNull { (sourceId, values) ->
          val rootName = values.firstOrNull()?.second?.rootName ?: return@mapNotNull null
          VideoSavedDirectoryEntry(
            key = "smb:$sourceId",
            name = rootName,
            location = VideoSavedBrowserLocation.SmbDirectory(
              sourceId = sourceId,
              rootName = rootName,
            ),
          )
        }
      val customFolders = if (sourceFilter == VideoSource.SMB) {
        emptyList()
      } else {
        folders.map { folder ->
          VideoSavedDirectoryEntry(
            key = "custom:${folder.id}",
            name = folder.name,
            location = VideoSavedBrowserLocation.CustomFolder(folder.id, folder.name),
          )
        }
      }
      val rootVideos = savedItems.filter { item ->
        when (item.source) {
          VideoSource.SMB -> item.id !in mappedSmbIds
          VideoSource.WEB,
          VideoSource.SERVICE,
          -> item.savedState?.folderId == null
        }
      }
      VideoSavedBrowserContent(
        directories = (smbRoots + customFolders).sortedWith(
          compareBy<VideoSavedDirectoryEntry> { it.name.lowercase() }.thenBy { it.key },
        ),
        videos = rootVideos.sortedBy { it.title.lowercase() },
        breadcrumbs = listOf(VideoSavedBreadcrumb("保存済み", VideoSavedBrowserLocation.Root)),
      )
    }

    is VideoSavedBrowserLocation.CustomFolder -> {
      val videos = savedItems
        .filter { it.source != VideoSource.SMB && it.savedState?.folderId == location.id }
        .sortedBy { it.title.lowercase() }
      VideoSavedBrowserContent(
        directories = emptyList(),
        videos = videos,
        breadcrumbs = listOf(
          VideoSavedBreadcrumb("保存済み", VideoSavedBrowserLocation.Root),
          VideoSavedBreadcrumb(location.name, location),
        ),
      )
    }

    is VideoSavedBrowserLocation.SmbDirectory -> {
      val matching = savedItems
        .asSequence()
        .filter { it.source == VideoSource.SMB }
        .mapNotNull { item -> item.smbBrowserPath(smbSources)?.let { path -> item to path } }
        .filter { (_, path) -> path.sourceId == location.sourceId }
        .filter { (_, path) -> path.directories.startsWithSegments(location.path) }
        .toList()
      val childDirectories = matching
        .mapNotNull { (_, path) -> path.directories.getOrNull(location.path.size) }
        .distinct()
        .sortedBy { it.lowercase() }
        .map { child ->
          val childPath = location.path + child
          VideoSavedDirectoryEntry(
            key = "smb:${location.sourceId}:${childPath.joinToString("/")}",
            name = child,
            location = location.copy(path = childPath),
          )
        }
      val videos = matching
        .filter { (_, path) -> path.directories == location.path }
        .map { it.first }
        .sortedBy { it.title.lowercase() }
      VideoSavedBrowserContent(
        directories = childDirectories,
        videos = videos,
        breadcrumbs = buildList {
          add(VideoSavedBreadcrumb("保存済み", VideoSavedBrowserLocation.Root))
          add(
            VideoSavedBreadcrumb(
              location.rootName,
              location.copy(path = emptyList()),
            ),
          )
          location.path.indices.forEach { index ->
            add(
              VideoSavedBreadcrumb(
                location.path[index],
                location.copy(path = location.path.take(index + 1)),
              ),
            )
          }
        },
      )
    }
  }
}

private fun List<String>.startsWithSegments(prefix: List<String>): Boolean {
  if (prefix.size > size) return false
  return prefix.indices.all { index -> this[index] == prefix[index] }
}
