package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollableOverlayPresentationSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  private data class ScrollableOverlay(
    val path: String,
    val startMarker: String,
    val endMarker: String,
  )

  private val overlays = listOf(
    ScrollableOverlay(
      path = "feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssWebScrapingRulesUi.kt",
      startMarker = "private fun RssWebScrapingRuleEditorDialog(",
      endMarker = "\n@Composable\nprivate fun RssWebScrapingPreviewCard",
    ),
    ScrollableOverlay(
      path = "feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryMetadataExtractorEditorBottomSheet.kt",
      startMarker = "internal fun WebLibraryMetadataExtractorEditorBottomSheet(",
      endMarker = "\n@Composable\nprivate fun WebLibraryMetadataExtractorTestResultCard",
    ),
    ScrollableOverlay(
      path = "feature/summary/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryDialog.kt",
      startMarker = "fun SummaryDialog(",
      endMarker = "\n}",
    ),
    ScrollableOverlay(
      path = "feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/TagManagerScreen.kt",
      startMarker = "selectedTag?.let { tag ->",
      endMarker = "\n  if (confirmingDeleteUnused)",
    ),
    ScrollableOverlay(
      path = "feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryScreen.kt",
      startMarker = "private fun LibrarySeriesBooksSheet(",
      endMarker = "\n@Composable\nprivate fun LibrarySearchField",
    ),
  )

  @Test
  fun `縦スクロール主体のoverlayはswipe dismissを持たない`() {
    overlays.forEach { overlay ->
      val source = source(overlay.path)
      assertTrue("start marker not found: ${overlay.path}", overlay.startMarker in source)
      assertTrue("end marker not found: ${overlay.path}", overlay.endMarker in source)
      val target = source.substringAfter(overlay.startMarker).substringBefore(overlay.endMarker)

      assertFalse(
        "scrollable overlay must not use ModalBottomSheet: ${overlay.path}",
        "ModalBottomSheet" in target,
      )
      assertTrue(
        "scrollable overlay must use a full-screen Dialog: ${overlay.path}",
        "Dialog(" in target && "usePlatformDefaultWidth = false" in target,
      )
      assertTrue(
        "scrollable overlay must not dismiss from outside gestures: ${overlay.path}",
        "dismissOnClickOutside = false" in target,
      )
    }
  }

  private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()
}
