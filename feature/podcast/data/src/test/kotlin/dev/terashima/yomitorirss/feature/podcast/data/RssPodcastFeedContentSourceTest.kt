package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.feature.podcast.PodcastSource
import dev.terashima.yomitorirss.feature.rss.RssFeedContentEntry
import dev.terashima.yomitorirss.feature.rss.RssFeedContentReader
import dev.terashima.yomitorirss.feature.rss.RssFeedContentSource
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Test

class RssPodcastFeedContentSourceTest {
  @Test
  fun `正規化したタイトルが一致する記事は重複を除外する`() = runSuspend {
    val reader = FakeReader(
      listOf(
        entry(identityKey = "first", feedId = "source-1", title = "AI News  Update"),
        entry(identityKey = "duplicate", feedId = "source-2", title = "ＡＩ Ｎｅｗｓ Update "),
        entry(identityKey = "other", feedId = "source-2", title = "Different news"),
      ),
    )
    val source = RssPodcastFeedContentSource(reader)

    val result = source.latestEntries(
      sources = listOf(
        PodcastSource("source-1", "Source 1", "https://example.invalid/1.xml"),
        PodcastSource("source-2", "Source 2", "https://example.invalid/2.xml"),
      ),
      limit = 10,
    )

    assertEquals(listOf("source-1:first", "source-2:other"), result.map { it.articleId })
  }

  @Test
  fun `異なるタイトルの記事はそのまま返す`() = runSuspend {
    val reader = FakeReader(
      listOf(
        entry(identityKey = "first", feedId = "source-1", title = "News A"),
        entry(identityKey = "second", feedId = "source-2", title = "News B"),
      ),
    )
    val source = RssPodcastFeedContentSource(reader)

    val result = source.latestEntries(
      sources = listOf(PodcastSource("source-1", "Source 1", "https://example.invalid/1.xml")),
      limit = 10,
    )

    assertEquals(listOf("source-1:first", "source-2:second"), result.map { it.articleId })
  }
}

private class FakeReader(
  private val entries: List<RssFeedContentEntry>,
) : RssFeedContentReader {
  override suspend fun latestEntries(feedIds: Set<String>, limit: Int): List<RssFeedContentEntry> =
    entries.take(limit)

  override suspend fun latestEntriesFromSources(
    sources: List<RssFeedContentSource>,
    limit: Int,
  ): List<RssFeedContentEntry> = entries.take(limit)
}

private fun entry(
  identityKey: String,
  feedId: String,
  title: String,
) = RssFeedContentEntry(
  identityKey = identityKey,
  feedId = feedId,
  title = title,
  sourceTitle = "Source",
  publishedAtEpochMillis = 1L,
  url = "https://example.invalid/$identityKey",
  content = "content $identityKey",
)

private fun runSuspend(block: suspend () -> Unit) {
  var failure: Throwable? = null
  block.startCoroutine(object : Continuation<Unit> {
    override val context = EmptyCoroutineContext

    override fun resumeWith(result: Result<Unit>) {
      failure = result.exceptionOrNull()
    }
  })
  failure?.let { throw it }
}
