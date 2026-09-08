package dev.terashima.yomitorirss.feature.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSmbBrowserTest {
  @Test
  fun `SMBとWebはcatalogに存在すれば保存済みとして扱う`() {
    assertTrue(item(VideoSource.SMB).isSaved)
    assertTrue(item(VideoSource.WEB).isSaved)
    assertFalse(item(VideoSource.SERVICE).isSaved)
    assertTrue(
      item(
        VideoSource.SERVICE,
        VideoSavedState(savedAtEpochMillis = 1L),
      ).isSaved,
    )
  }

  @Test
  fun `SMB同期rootから相対directoryを導出する`() {
    val item = VideoItem(
      id = "video-1",
      source = VideoSource.SMB,
      sourceId = "mosaic-smb-video://file?serverId=server-1&share=media&path=videos%5Cmovies%5Cseries%5Cmovie.mp4",
      title = "movie",
      updatedAtEpochMillis = 1L,
    )
    val path = item.smbBrowserPath(
      listOf(
        VideoSmbSource(
          id = "source-1",
          serverId = "server-1",
          share = "media",
          rootPath = "videos\\movies",
        ),
      ),
    )

    assertEquals("source-1", path?.sourceId)
    assertEquals("movies", path?.rootName)
    assertEquals(listOf("series"), path?.directories)
  }

  @Test
  fun `重複するSMB同期rootでは最も深いrootを選ぶ`() {
    val item = VideoItem(
      id = "video-1",
      source = VideoSource.SMB,
      sourceId = "mosaic-smb-video://file?serverId=server-1&share=media&path=videos%5Cmovies%5Cseries%5Cmovie.mp4",
      title = "movie",
      updatedAtEpochMillis = 1L,
    )
    val path = item.smbBrowserPath(
      listOf(
        VideoSmbSource("source-wide", "server-1", "media", "videos"),
        VideoSmbSource("source-specific", "server-1", "media", "videos\\movies"),
      ),
    )

    assertEquals("source-specific", path?.sourceId)
    assertEquals(listOf("series"), path?.directories)
  }

  private fun item(source: VideoSource, savedState: VideoSavedState? = null) = VideoItem(
    id = source.name,
    source = source,
    sourceId = source.name,
    title = source.name,
    updatedAtEpochMillis = 1L,
    savedState = savedState,
  )
}
