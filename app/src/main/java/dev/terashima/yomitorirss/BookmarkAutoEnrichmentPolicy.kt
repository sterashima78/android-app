package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.bookmark.data.BookmarkSourceMetadata
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.youtube.isYouTubeVideoUrl

internal fun shouldRequestBookmarkEnrichment(source: BookmarkSourceMetadata): Boolean =
  !isYouTubeVideoUrl(source.url) &&
    !isRedditFeedUrl(source.sourceFeedUrl) &&
    !isRedditFeedUrl(source.url)
