package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.youtube.isYouTubeVideoUrl

internal fun shouldRequestBookmarkEnrichment(
  url: String,
  sourceFeedUrl: String,
): Boolean =
  !isYouTubeVideoUrl(url) &&
    !isRedditFeedUrl(sourceFeedUrl) &&
    !isRedditFeedUrl(url)
