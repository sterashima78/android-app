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

data class TrafficUsageOverview(
  val records: List<TrafficUsageRecord>,
  val systemMobileBytes: Long?,
  val systemWifiBytes: Long?,
)

/** Read-only diagnostic projection. This is not user data and is not backed up. */
fun interface TrafficUsageReader {
  fun read(days: Int): TrafficUsageOverview
}
