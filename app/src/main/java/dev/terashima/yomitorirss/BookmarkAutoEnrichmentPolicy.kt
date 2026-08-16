package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.allowsAutomaticAiEnrichment
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.youtube.isYouTubeVideoUrl

internal fun shouldRequestBookmarkEnrichment(
  url: String,
  sourceFeedUrl: String,
  contentType: ContentType = ContentType.ARTICLE,
): Boolean =
  contentType.allowsAutomaticAiEnrichment() &&
    !isYouTubeVideoUrl(url) &&
    !isRedditFeedUrl(sourceFeedUrl) &&
    !isRedditFeedUrl(url)
