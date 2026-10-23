package dev.terashima.yomitorirss.feature.rss

/** Available values for per-source automatic refresh and RSS screen refresh. */
val RSS_REFRESH_INTERVAL_OPTIONS = listOf(5L, 15L, 30L, 60L, 180L, 360L, 720L, 1440L, 2880L, 4320L, 10080L)

fun requireRssRefreshInterval(minutes: Long?) {
  require(minutes == null || minutes in RSS_REFRESH_INTERVAL_OPTIONS) {
    "取得間隔を選択肢から指定してください"
  }
}

fun rssRefreshIntervalLabel(minutes: Long): String = when {
  minutes < 60 -> "${minutes}分"
  minutes % 1440L == 0L -> "${minutes / 1440L}日"
  else -> "${minutes / 60L}時間"
}
