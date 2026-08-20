package dev.terashima.yomitorirss.feature.health.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.terashima.yomitorirss.feature.health.BodyFatMeasurement
import dev.terashima.yomitorirss.feature.health.DailyNutritionIntake
import dev.terashima.yomitorirss.feature.health.HealthAvailability
import dev.terashima.yomitorirss.feature.health.HealthExerciseSegmentType
import dev.terashima.yomitorirss.feature.health.HealthOverview
import dev.terashima.yomitorirss.feature.health.HealthRepository
import dev.terashima.yomitorirss.feature.health.HealthWorkoutSession
import dev.terashima.yomitorirss.feature.health.HealthWorkoutWriteResult
import dev.terashima.yomitorirss.feature.health.HealthWorkoutWriter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HealthConnectHealthRepository(context: Context) : HealthRepository, HealthWorkoutWriter {
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
      bodyFatMeasurements = readBodyFatMeasurements(timeRange),
      nutritionDailyIntakes = readDailyNutrition(timeRange),
    )
  }

  override suspend fun writeWorkout(session: HealthWorkoutSession): HealthWorkoutWriteResult {
    if (availability() != HealthAvailability.AVAILABLE) return HealthWorkoutWriteResult.UNAVAILABLE
    val grantedPermissions = client.permissionController.getGrantedPermissions()
    if (!grantedPermissions.containsAll(WRITE_PERMISSIONS)) {
      return HealthWorkoutWriteResult.PERMISSION_REQUIRED
    }

    require(session.startTime < session.endTime) { "Workout startTime must be before endTime" }
    require(session.segments.all { it.startTime >= session.startTime && it.endTime <= session.endTime }) {
      "Workout segments must be contained in the session"
    }
    require(session.segments.zipWithNext().all { (current, next) -> current.endTime <= next.startTime }) {
      "Workout segments must not overlap"
    }

    val record = ExerciseSessionRecord(
      startTime = session.startTime,
      startZoneOffset = zoneOffsetAt(session.startTime),
      endTime = session.endTime,
      endZoneOffset = zoneOffsetAt(session.endTime),
      metadata = Metadata.manualEntry(clientRecordId = session.clientRecordId),
      exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
      title = session.title,
      notes = session.notes,
      segments = session.segments.map { segment ->
        ExerciseSegment(
          startTime = segment.startTime,
          endTime = segment.endTime,
          segmentType = segmentType(segment.type),
          repetitions = segment.repetitions.coerceAtLeast(0),
        )
      },
    )
    client.insertRecords(listOf(record))
    return HealthWorkoutWriteResult.WRITTEN
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

  private suspend fun readBodyFatMeasurements(timeRange: TimeRangeFilter): List<BodyFatMeasurement> {
    val measurements = mutableListOf<BodyFatMeasurement>()
    var pageToken: String? = null
    do {
      val response = client.readRecords(
        ReadRecordsRequest(
          recordType = BodyFatRecord::class,
          timeRangeFilter = timeRange,
          ascendingOrder = true,
          pageSize = 1000,
          pageToken = pageToken,
        ),
      )
      response.records.forEach { record ->
        measurements += BodyFatMeasurement(
          time = record.time,
          percentage = record.percentage.value,
        )
      }
      pageToken = response.pageToken
    } while (pageToken != null)
    return measurements
  }

  private suspend fun readDailyNutrition(timeRange: TimeRangeFilter): List<DailyNutritionIntake> {
    val samples = mutableListOf<NutritionSample>()
    var pageToken: String? = null
    do {
      val response = client.readRecords(
        ReadRecordsRequest(
          recordType = NutritionRecord::class,
          timeRangeFilter = timeRange,
          ascendingOrder = true,
          pageSize = 1000,
          pageToken = pageToken,
        ),
      )
      response.records.forEach { record ->
        if (
          record.energy != null ||
          record.protein != null ||
          record.totalFat != null ||
          record.totalCarbohydrate != null
        ) {
          val offset = record.startZoneOffset ?: zoneOffsetAt(record.startTime)
          samples += NutritionSample(
            date = record.startTime.atOffset(offset).toLocalDate(),
            energyKcal = record.energy?.inKilocalories ?: 0.0,
            proteinGrams = record.protein?.inGrams ?: 0.0,
            fatGrams = record.totalFat?.inGrams ?: 0.0,
            carbohydrateGrams = record.totalCarbohydrate?.inGrams ?: 0.0,
          )
        }
      }
      pageToken = response.pageToken
    } while (pageToken != null)
    return aggregateNutritionByDay(samples)
  }

  private fun segmentType(type: HealthExerciseSegmentType): Int = when (type) {
    HealthExerciseSegmentType.CRUNCH -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH
    HealthExerciseSegmentType.LUNGE -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE
    HealthExerciseSegmentType.PLANK -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK
    HealthExerciseSegmentType.STAIR_CLIMBING -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING
    HealthExerciseSegmentType.OTHER -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
  }

  private fun zoneOffsetAt(instant: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(instant)

  companion object {
    val READ_PERMISSIONS: Set<String> = setOf(
      HealthPermission.getReadPermission(StepsRecord::class),
      HealthPermission.getReadPermission(ExerciseSessionRecord::class),
      HealthPermission.getReadPermission(HeartRateRecord::class),
      HealthPermission.getReadPermission(SleepSessionRecord::class),
      HealthPermission.getReadPermission(WeightRecord::class),
      HealthPermission.getReadPermission(BodyFatRecord::class),
      HealthPermission.getReadPermission(NutritionRecord::class),
    )

    val WRITE_PERMISSIONS: Set<String> = setOf(
      HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    val REQUEST_PERMISSIONS: Set<String> = READ_PERMISSIONS + WRITE_PERMISSIONS

    private val AGGREGATE_METRICS: Set<AggregateMetric<*>> = setOf(
      StepsRecord.COUNT_TOTAL,
      HeartRateRecord.BPM_AVG,
      SleepSessionRecord.SLEEP_DURATION_TOTAL,
      WeightRecord.WEIGHT_AVG,
    )
  }
}

internal data class NutritionSample(
  val date: LocalDate,
  val energyKcal: Double,
  val proteinGrams: Double,
  val fatGrams: Double,
  val carbohydrateGrams: Double,
)

internal fun aggregateNutritionByDay(samples: List<NutritionSample>): List<DailyNutritionIntake> =
  samples
    .groupBy(NutritionSample::date)
    .map { (date, dailySamples) ->
      DailyNutritionIntake(
        date = date,
        energyKcal = dailySamples.sumOf(NutritionSample::energyKcal),
        proteinGrams = dailySamples.sumOf(NutritionSample::proteinGrams),
        fatGrams = dailySamples.sumOf(NutritionSample::fatGrams),
        carbohydrateGrams = dailySamples.sumOf(NutritionSample::carbohydrateGrams),
      )
    }
    .sortedBy(DailyNutritionIntake::date)
