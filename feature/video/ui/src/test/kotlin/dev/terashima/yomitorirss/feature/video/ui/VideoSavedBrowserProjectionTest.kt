package dev.terashima.yomitorirss.feature.video.ui

import dev.terashima.yomitorirss.feature.video.VideoFolder
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoSavedState
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSavedBrowserProjectionTest {
  private val smbSource = VideoSmbSource(
    id = "smb-source",
    serverId = "server-1",
    share = "media",
    rootPath = "videos",
  )

  @Test
  fun `rootにはSMB同期rootとcustom folderと未分類動画を表示する`() {
    val content = buildVideoSavedBrowserContent(
      items = listOf(
        smb("root.mp4"),
        web("web-root", folderId = null),
        web("web-folder", folderId = "folder-1"),
        service("service-root", saved = true),
        service("service-unsaved", saved = false),
      ),
      folders = listOf(VideoFolder("folder-1", "お気に入り")),
      smbSources = listOf(smbSource),
      location = VideoSavedBrowserLocation.Root,
      sourceFilter = null,
    )

    assertEquals(listOf("videos", "お気に入り"), content.directories.map { it.name }.sorted())
    assertEquals(listOf("service-root", "web-root"), content.videos.map { it.title }.sorted())
    assertTrue(content.videos.none { it.title == "service-unsaved" })
  }

  @Test
  fun `SMB directoryでは直下folderと動画を同じ階層として投影する`() {
    val content = buildVideoSavedBrowserContent(
      items = listOf(
        smb("movie.mp4"),
        smb("series\\episode-1.mp4"),
        smb("series\\season-2\\episode-2.mp4"),
      ),
      folders = emptyList(),
      smbSources = listOf(smbSource),
      location = VideoSavedBrowserLocation.SmbDirectory(
        sourceId = "smb-source",
        rootName = "videos",
      ),
      sourceFilter = null,
    )

    assertEquals(listOf("series"), content.directories.map { it.name })
    assertEquals(listOf("movie"), content.videos.map { it.title })
  }

  @Test
  fun `custom folderではWebと明示保存済みProviderだけを表示する`() {
    val content = buildVideoSavedBrowserContent(
      items = listOf(
        web("web-folder", folderId = "folder-1"),
        service("service-folder", saved = true, folderId = "folder-1"),
        smb("folder-1\\smb.mp4"),
      ),
      folders = listOf(VideoFolder("folder-1", "お気に入り")),
      smbSources = listOf(smbSource),
      location = VideoSavedBrowserLocation.CustomFolder("folder-1", "お気に入り"),
      sourceFilter = null,
    )

    assertEquals(listOf("service-folder", "web-folder"), content.videos.map { it.title }.sorted())
  }

  private fun smb(relativePath: String): VideoItem {
    val encodedPath = ("videos\\$relativePath")
      .replace("%", "%25")
      .replace("\\", "%5C")
      .replace(" ", "+")
    val fileName = relativePath.substringAfterLast('\\')
    return VideoItem(
      id = "smb:$relativePath",
      source = VideoSource.SMB,
      sourceId = "mosaic-smb-video://file?serverId=server-1&share=media&path=$encodedPath",
      title = fileName.substringBeforeLast('.'),
      updatedAtEpochMillis = 1L,
    )
  }

  private fun web(title: String, folderId: String?): VideoItem = VideoItem(
    id = title,
    source = VideoSource.WEB,
    sourceId = "https://example.invalid/$title",
    title = title,
    updatedAtEpochMillis = 1L,
    savedState = folderId?.let { VideoSavedState(it, 1L) },
  )

  private fun service(
    title: String,
    saved: Boolean,
    folderId: String? = null,
  ): VideoItem = VideoItem(
    id = title,
    source = VideoSource.SERVICE,
    sourceId = title,
    title = title,
    updatedAtEpochMillis = 1L,
    savedState = if (saved) VideoSavedState(folderId, 1L) else null,
  )
}
