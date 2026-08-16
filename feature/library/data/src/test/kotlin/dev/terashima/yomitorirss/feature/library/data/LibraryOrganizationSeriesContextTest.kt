package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryCollection
import dev.terashima.yomitorirss.feature.library.LibraryItemOrganization
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationTag
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.organizationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryOrganizationSeriesContextTest {
  @Test
  fun `same series reuses classifications from already organized peer books`() {
    val target = seriesBook("target", seriesId = "series-1", seriesName = "Example Series")
    val peer = seriesBook("peer", seriesId = "series-1", seriesName = "Example Series")
    val unrelated = seriesBook("other", seriesId = "series-2", seriesName = "Other Series")
    val sharedTag = LibraryOrganizationTag("tag-1", "共通テーマ", "共通テーマ")
    val sharedCollection = LibraryCollection("collection-1", "シリーズ棚", "シリーズ棚")
    val unrelatedTag = LibraryOrganizationTag("tag-2", "別テーマ", "別テーマ")
    val snapshot = LibraryOrganizationSnapshot(
      tags = listOf(sharedTag, unrelatedTag),
      collections = listOf(sharedCollection),
      items = mapOf(
        peer.organizationKey() to LibraryItemOrganization(
          key = peer.organizationKey(),
          tags = listOf(sharedTag),
          collections = listOf(sharedCollection),
        ),
        unrelated.organizationKey() to LibraryItemOrganization(
          key = unrelated.organizationKey(),
          tags = listOf(unrelatedTag),
        ),
      ),
    )

    val context = seriesOrganizationContextFor(
      book = target,
      books = listOf(target, peer, unrelated),
      organizationSnapshot = snapshot,
    )

    assertEquals(listOf("共通テーマ"), context?.tagNames)
    assertEquals(listOf("シリーズ棚"), context?.collectionNames)
  }

  @Test
  fun `different explicit series ids do not match only because names are equal`() {
    val target = seriesBook("target", seriesId = "series-1", seriesName = "Same Name")
    val other = seriesBook("other", seriesId = "series-2", seriesName = "Same Name")
    val tag = LibraryOrganizationTag("tag-1", "別シリーズ", "別シリーズ")
    val snapshot = LibraryOrganizationSnapshot(
      tags = listOf(tag),
      items = mapOf(
        other.organizationKey() to LibraryItemOrganization(
          key = other.organizationKey(),
          tags = listOf(tag),
        ),
      ),
    )

    val context = seriesOrganizationContextFor(
      book = target,
      books = listOf(target, other),
      organizationSnapshot = snapshot,
    )

    assertNull(context)
  }

  @Test
  fun `series name is used when a peer has no structured series id`() {
    val target = seriesBook("target", seriesId = "series-1", seriesName = "Fallback Series")
    val peer = seriesBook("peer", seriesId = null, seriesName = " fallback series ")
    val tag = LibraryOrganizationTag("tag-1", "共通タグ", "共通タグ")
    val snapshot = LibraryOrganizationSnapshot(
      tags = listOf(tag),
      items = mapOf(
        peer.organizationKey() to LibraryItemOrganization(
          key = peer.organizationKey(),
          tags = listOf(tag),
        ),
      ),
    )

    val context = seriesOrganizationContextFor(
      book = target,
      books = listOf(target, peer),
      organizationSnapshot = snapshot,
    )

    assertEquals(listOf("共通タグ"), context?.tagNames)
  }
}

private fun seriesBook(
  sourceId: String,
  seriesId: String?,
  seriesName: String,
): LibraryBook = LibraryBook(
  source = LibrarySource.KINDLE,
  sourceId = sourceId,
  title = "Test Book $sourceId",
  authors = listOf("Test Author"),
  publisher = null,
  publishedDate = null,
  description = null,
  isbn10 = null,
  isbn13 = null,
  thumbnailUrl = null,
  infoUrl = null,
  series = LibrarySeries(
    name = seriesName,
    position = 1,
    id = seriesId,
  ),
)
