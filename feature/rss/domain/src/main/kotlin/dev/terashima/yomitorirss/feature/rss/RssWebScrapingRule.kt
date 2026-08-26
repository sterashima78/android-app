package dev.terashima.yomitorirss.feature.rss

const val DEFAULT_RSS_WEB_SCRAPING_TIMEOUT_SECONDS = 15
const val MIN_RSS_WEB_SCRAPING_TIMEOUT_SECONDS = 5
const val MAX_RSS_WEB_SCRAPING_TIMEOUT_SECONDS = 120

data class RssWebScrapingRule(
  val id: String,
  val urlPattern: String,
  val functionCode: String,
  val timeoutSeconds: Int = DEFAULT_RSS_WEB_SCRAPING_TIMEOUT_SECONDS,
  val updatedAt: Long,
)

data class RssWebScrapingItemPreview(
  val title: String,
  val url: String,
  val externalId: String? = null,
  val publishedAt: String? = null,
)

data class RssWebScrapingPreview(
  val title: String,
  val siteUrl: String?,
  val items: List<RssWebScrapingItemPreview>,
)
