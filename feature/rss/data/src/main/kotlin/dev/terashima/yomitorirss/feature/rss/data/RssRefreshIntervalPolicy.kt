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

/** A never-fetched feed, missing timestamp or future timestamp is due. */
internal fun rssIsRefreshDue(nowMillis: Long, lastSuccessMillis: Long?, minimumAgeMillis: Long): Boolean {
  if (minimumAgeMillis == 0L || lastSuccessMillis == null) return true
  val elapsed = nowMillis - lastSuccessMillis
  return elapsed < 0L || elapsed >= minimumAgeMillis
}
