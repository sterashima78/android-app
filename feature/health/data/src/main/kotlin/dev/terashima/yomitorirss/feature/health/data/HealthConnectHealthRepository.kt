package dev.terashima.yomitorirss.feature.health.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.terashima.yomitorirss.feature.health.HealthAvailability
import dev.terashima.yomitorirss.feature.health.HealthOverview
import dev.terashima.yomitorirss.feature.health.HealthRepository
import java.time.Duration
import java.time.Instant

class HealthConnectHealthRepository(context: Context) : HealthRepository {
  private val applicationContext = context.applicationContext

  private val client: HealthConnectClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    HealthConnectClient.getOrCreate(applicationContext)
  }

  override fun availability(): HealthAvailability =
    when (HealthConnectClient.getSdkStatus(applicationContext)) {
      HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
      HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.PROVIDER_UPDATE_REQUIRED
      else -> HealthAvailability.UNAVAILABLE
    }

  override suspend fun hasRequiredPermissions(): Boolean {
    if (availability() != HealthAvailability.AVAILABLE) return false
    return client.permissionController.getGrantedPermissions().containsAll(READ_PERMISSIONS)
  }

  override suspend fun readOverview(startTime: Instant, endTime: Instant): HealthOverview {
    require(startTime < endTime) { "startTime must be before endTime" }
    val timeRange = TimeRangeFilter.between(startTime, endTime)
    val aggregation = client.aggregate(
      AggregateRequest(
        metrics = AGGREGATE_METRICS,
        timeRangeFilter = timeRange,
      ),
    )
    return HealthOverview(
      steps = aggregation[StepsRecord.COUNT_TOTAL],
      exerciseMinutes = readExerciseMinutes(timeRange),
      averageHeartRateBpm = aggregation[HeartRateRecord.BPM_AVG],
      sleepMinutes = aggregation[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes(),
      averageWeightKg = aggregation[WeightRecord.WEIGHT_AVG]?.inKilograms,
    )
  }

  private suspend fun readExerciseMinutes(timeRange: TimeRangeFilter): Long? {
    var pageToken: String? = null
    var total = Duration.ZERO
    var hasRecords = false
    do {
      val response = client.readRecords(
        ReadRecordsRequest(
          recordType = ExerciseSessionRecord::class,
          timeRangeFilter = timeRange,
          pageSize = 1000,
          pageToken = pageToken,
        ),
      )
      response.records.forEach { session ->
        hasRecords = true
        total = total.plus(Duration.between(session.startTime, session.endTime))
      }
      pageToken = response.pageToken
    } while (pageToken != null)
    return if (hasRecords) total.toMinutes() else null
  }

  companion object {
    val READ_PERMISSIONS: Set<String> = setOf(
      HealthPermission.getReadPermission(StepsRecord::class),
      HealthPermission.getReadPermission(ExerciseSessionRecord::class),
      HealthPermission.getReadPermission(HeartRateRecord::class),
      HealthPermission.getReadPermission(SleepSessionRecord::class),
      HealthPermission.getReadPermission(WeightRecord::class),
    )

    private val AGGREGATE_METRICS: Set<AggregateMetric<*>> = setOf(
      StepsRecord.COUNT_TOTAL,
      HeartRateRecord.BPM_AVG,
      SleepSessionRecord.SLEEP_DURATION_TOTAL,
      WeightRecord.WEIGHT_AVG,
    )
  }
}
