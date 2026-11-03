package dev.terashima.yomitorirss.feature.settings

data class TrafficUsageRecord(
  val date: String,
  val feature: String,
  val transport: String,
  val receivedBytes: Long,
  val sentBytes: Long,
) {
  val totalBytes: Long get() = receivedBytes + sentBytes
}

data class RssFeedTrafficRecord(
  val date: String,
  val feedId: String,
  val feedTitle: String?,
  val transport: String,
  val origin: String,
  val receivedBytes: Long,
  val requests: Long,
  val notModified: Long,
)

data class RssFeedTrafficSummary(
  val feedId: String,
  val feedTitle: String?,
  val receivedBytes: Long,
  val requests: Long,
  val notModified: Long,
  val origins: Map<String, Long>,
) {
  val notModifiedPercent: Long get() = if (requests == 0L) 0L else notModified * 100L / requests
}

fun TrafficUsageOverview.topRssFeeds(
  transport: String,
  limit: Int = 10,
  date: String? = null,
): List<RssFeedTrafficSummary> =
  feedRecords
    .asSequence()
    .filter { (transport == "all" || it.transport == transport) && (date == null || it.date == date) }
    .groupBy(RssFeedTrafficRecord::feedId)
    .map { (id, records) ->
      RssFeedTrafficSummary(
        feedId = id,
        feedTitle = records.firstNotNullOfOrNull(RssFeedTrafficRecord::feedTitle),
        receivedBytes = records.sumOf(RssFeedTrafficRecord::receivedBytes),
        requests = records.sumOf(RssFeedTrafficRecord::requests),
        notModified = records.sumOf(RssFeedTrafficRecord::notModified),
        origins = records.groupBy(RssFeedTrafficRecord::origin)
          .mapValues { (_, grouped) -> grouped.sumOf(RssFeedTrafficRecord::requests) },
      )
    }
    .sortedWith(compareByDescending<RssFeedTrafficSummary> { it.receivedBytes }.thenBy { it.feedId })
    .take(limit.coerceAtLeast(0))

data class TrafficUsageOverview(
  val records: List<TrafficUsageRecord>,
  val systemMobileBytes: Long?,
  val systemWifiBytes: Long?,
  val feedRecords: List<RssFeedTrafficRecord> = emptyList(),
)

/** Read-only diagnostic projection. This is not user data and is not backed up. */
fun interface TrafficUsageReader {
  fun read(days: Int): TrafficUsageOverview
}

data class TrafficFeatureSummary(
  val feature: String,
  val receivedBytes: Long,
  val sentBytes: Long,
) {
  val totalBytes: Long get() = receivedBytes + sentBytes
}

data class TrafficDailySummary(
  val date: String,
  val receivedBytes: Long,
  val sentBytes: Long,
  val features: List<TrafficFeatureSummary>,
) {
  val totalBytes: Long get() = receivedBytes + sentBytes
}

/** Daily diagnostic breakdown using the same transport filter as the period totals. */
fun TrafficUsageOverview.dailySummaries(transport: String): List<TrafficDailySummary> =
  records
    .asSequence()
    .filter { transport == "all" || it.transport == transport }
    .groupBy(TrafficUsageRecord::date)
    .map { (date, rows) ->
      TrafficDailySummary(
        date = date,
        receivedBytes = rows.sumOf(TrafficUsageRecord::receivedBytes),
        sentBytes = rows.sumOf(TrafficUsageRecord::sentBytes),
        features = rows.groupBy(TrafficUsageRecord::feature)
          .map { (feature, featureRows) ->
            TrafficFeatureSummary(
              feature = feature,
              receivedBytes = featureRows.sumOf(TrafficUsageRecord::receivedBytes),
              sentBytes = featureRows.sumOf(TrafficUsageRecord::sentBytes),
            )
          }
          .sortedWith(compareByDescending<TrafficFeatureSummary> { it.totalBytes }.thenBy { it.feature }),
      )
    }
    .sortedByDescending(TrafficDailySummary::date)
