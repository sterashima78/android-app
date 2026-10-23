package dev.terashima.yomitorirss.feature.rss.data

/** A force request (zero age) must never be converted into an automatic request. */
internal fun rssEffectiveMinimumAgeMillis(
  requestedAgeMillis: Long,
  feedOverrideMinutes: Long?,
  ruleOverrideMinutes: Long?,
): Long {
  require(requestedAgeMillis >= 0)
  if (requestedAgeMillis == 0L) return 0L
  return (feedOverrideMinutes ?: ruleOverrideMinutes)?.times(60_000L) ?: requestedAgeMillis
}
