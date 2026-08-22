package dev.terashima.yomitorirss.feature.health.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.terashima.yomitorirss.feature.health.BodyFatMeasurement
import dev.terashima.yomitorirss.feature.health.DailyHealthSummary
import dev.terashima.yomitorirss.feature.health.DailyNutritionIntake
import dev.terashima.yomitorirss.feature.health.HealthAvailability
import dev.terashima.yomitorirss.feature.health.HealthExerciseSegmentSummary
import dev.terashima.yomitorirss.feature.health.HealthExerciseSegmentType
import dev.terashima.yomitorirss.feature.health.HealthExerciseSessionSummary
import dev.terashima.yomitorirss.feature.health.HealthHistoryAccess
import dev.terashima.yomitorirss.feature.health.HealthOverview
import dev.terashima.yomitorirss.feature.health.HealthRepository
import dev.terashima.yomitorirss.feature.health.HealthWorkoutSession
import dev.terashima.yomitorirss.feature.health.HealthWorkoutWriteResult
import dev.terashima.yomitorirss.feature.health.HealthWorkoutWriter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
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

  override suspend fun historyAccess(): HealthHistoryAccess {
    if (!historyFeatureAvailable()) return HealthHistoryAccess.UNSUPPORTED
    val grantedPermissions = client.permissionController.getGrantedPermissions()
    return if (HISTORY_PERMISSION in grantedPermissions) {
      HealthHistoryAccess.AVAILABLE
    } else {
      HealthHistoryAccess.PERMISSION_REQUIRED
    }
  }

  fun requestPermissions(): Set<String> =
    READ_PERMISSIONS + WRITE_PERMISSIONS +
      if (historyFeatureAvailable()) setOf(HISTORY_PERMISSION) else emptySet()

  override suspend fun readOverview(startTime: Instant, endTime: Instant): HealthOverview {
    require(startTime < endTime) { "startTime must be before endTime" }
    val timeRange = TimeRangeFilter.between(startTime, endTime)
    val aggregation = client.aggregate(
      AggregateRequest(
        metrics = AGGREGATE_METRICS,
        timeRangeFilter = timeRange,
      ),
    )
    val includeExerciseActivityMetrics = Duration.between(startTime, endTime) <= Duration.ofDays(8)
    val exerciseSessions = readExerciseSessions(
      timeRange = timeRange,
      includeActivityMetrics = includeExerciseActivityMetrics,
    )
    return HealthOverview(
      steps = aggregation[StepsRecord.COUNT_TOTAL],
      activeCaloriesKcal = aggregation[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
      exerciseMinutes = aggregation[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes(),
      averageHeartRateBpm = aggregation[HeartRateRecord.BPM_AVG],
      sleepMinutes = aggregation[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes(),
      averageWeightKg = aggregation[WeightRecord.WEIGHT_AVG]?.inKilograms,
      bodyFatMeasurements = readBodyFatMeasurements(timeRange),
      nutritionDailyIntakes = readDailyNutrition(timeRange),
      exerciseSessions = exerciseSessions,
      dailySummaries = readDailySummaries(startTime, endTime),
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

  private suspend fun readDailySummaries(startTime: Instant, endTime: Instant): List<DailyHealthSummary> {
    val zoneId = ZoneId.systemDefault()
    val localStart = startTime.atZone(zoneId).toLocalDateTime()
    val localEnd = endTime.atZone(zoneId).toLocalDateTime()
    val grouped = client.aggregateGroupByPeriod(
      AggregateGroupByPeriodRequest(
        metrics = AGGREGATE_METRICS,
        timeRangeFilter = TimeRangeFilter.between(localStart, localEnd),
        timeRangeSlicer = Period.ofDays(1),
      ),
    ).associateBy { it.startTime.toLocalDate() }

    val endDateExclusive = if (localEnd.toLocalTime() == LocalTime.MIDNIGHT) {
      localEnd.toLocalDate()
    } else {
      localEnd.toLocalDate().plusDays(1)
    }

    return generateSequence(localStart.toLocalDate()) { it.plusDays(1) }
      .takeWhile { it < endDateExclusive }
      .map { date ->
        val result = grouped[date]?.result
        DailyHealthSummary(
          date = date,
          steps = result?.get(StepsRecord.COUNT_TOTAL),
          activeCaloriesKcal = result?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
          exerciseMinutes = result?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes(),
          averageHeartRateBpm = result?.get(HeartRateRecord.BPM_AVG),
          sleepMinutes = result?.get(SleepSessionRecord.SLEEP_DURATION_TOTAL)?.toMinutes(),
          averageWeightKg = result?.get(WeightRecord.WEIGHT_AVG)?.inKilograms,
        )
      }
      .toList()
  }

  private suspend fun readExerciseSessions(
    timeRange: TimeRangeFilter,
    includeActivityMetrics: Boolean,
  ): List<HealthExerciseSessionSummary> {
    val sessions = mutableListOf<ExerciseSessionCandidate>()
    var pageToken: String? = null
    do {
      val response = client.readRecords(
        ReadRecordsRequest(
          recordType = ExerciseSessionRecord::class,
          timeRangeFilter = timeRange,
          ascendingOrder = false,
          pageSize = 1000,
          pageToken = pageToken,
        ),
      )
      response.records.forEach { record ->
        sessions += ExerciseSessionCandidate(
          exerciseType = record.exerciseType,
          dataOriginPackageName = record.metadata.dataOrigin.packageName,
          summary = HealthExerciseSessionSummary(
            startTime = record.startTime,
            endTime = record.endTime,
            exerciseName = exerciseSessionName(record.exerciseType),
            title = record.title?.takeIf(String::isNotBlank),
            notes = record.notes?.takeIf(String::isNotBlank),
            segments = record.segments
              .sortedBy(ExerciseSegment::startTime)
              .map { segment ->
                HealthExerciseSegmentSummary(
                  startTime = segment.startTime,
                  endTime = segment.endTime,
                  exerciseName = exerciseSegmentName(segment.segmentType),
                  repetitions = segment.repetitions,
                )
              },
          ),
        )
      }
      pageToken = response.pageToken
    } while (pageToken != null)

    val orderedSessions = deduplicateExerciseSessions(sessions)
      .sortedByDescending(HealthExerciseSessionSummary::startTime)
    return if (includeActivityMetrics) {
      orderedSessions.map { session -> enrichExerciseSessionActivity(session) }
    } else {
      orderedSessions
    }
  }

  private suspend fun enrichExerciseSessionActivity(
    session: HealthExerciseSessionSummary,
  ): HealthExerciseSessionSummary {
    if (session.startTime >= session.endTime) return session
    val aggregation = client.aggregate(
      AggregateRequest(
        metrics = EXERCISE_ACTIVITY_METRICS,
        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
      ),
    )
    return session.copy(
      activeCaloriesKcal = aggregation[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
      averageHeartRateBpm = aggregation[HeartRateRecord.BPM_AVG],
      steps = aggregation[StepsRecord.COUNT_TOTAL],
    )
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

  private fun historyFeatureAvailable(): Boolean =
    availability() == HealthAvailability.AVAILABLE &&
      client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
      HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

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
      HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
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

    const val HISTORY_PERMISSION: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    private val AGGREGATE_METRICS: Set<AggregateMetric<*>> = setOf(
      StepsRecord.COUNT_TOTAL,
      ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
      ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
      HeartRateRecord.BPM_AVG,
      SleepSessionRecord.SLEEP_DURATION_TOTAL,
      WeightRecord.WEIGHT_AVG,
    )

    private val EXERCISE_ACTIVITY_METRICS: Set<AggregateMetric<*>> = setOf(
      ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
      HeartRateRecord.BPM_AVG,
      StepsRecord.COUNT_TOTAL,
    )
  }
}

internal fun totalExerciseMinutes(sessions: List<HealthExerciseSessionSummary>): Long? {
  if (sessions.isEmpty()) return null
  val duration = sessions.fold(Duration.ZERO) { total, session ->
    total.plus(Duration.between(session.startTime, session.endTime))
  }
  return duration.toMinutes()
}

internal fun exerciseSessionName(exerciseType: Int): String = when (exerciseType) {
  ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "バドミントン"
  ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> "野球"
  ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "バスケットボール"
  ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "サイクリング"
  ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "エアロバイク"
  ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "ボクシング"
  ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "自重トレーニング"
  ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "ダンス"
  ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "エリプティカル"
  ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
  ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "ハイキング"
  ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "武道"
  ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "ピラティス"
  ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "ロッククライミング"
  ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "ローイング"
  ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "ローイングマシン"
  ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "ランニング"
  ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "トレッドミル"
  ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "階段昇降"
  ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "ステアクライマー"
  ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "筋力トレーニング"
  ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "ストレッチ"
  ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "オープンウォータースイミング"
  ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "水泳"
  ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "卓球"
  ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "テニス"
  ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "ウォーキング"
  ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "ウェイトリフティング"
  ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "ヨガ"
  ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "その他の運動"
  else -> "運動"
}

internal fun exerciseSegmentName(segmentType: Int): String = when (segmentType) {
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL -> "アームカール"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS -> "ベンチプレス"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING -> "サイクリング"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY -> "エアロバイク"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE -> "バーピー"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH -> "クランチ"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT -> "デッドリフト"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE -> "ランジ"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT -> "その他"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE -> "一時停止"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK -> "プランク"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP -> "懸垂"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST -> "休憩"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_ROWING_MACHINE -> "ローイングマシン"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING -> "ランニング"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL -> "トレッドミル"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP -> "シットアップ"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT -> "スクワット"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING -> "階段昇降"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING_MACHINE -> "ステアクライマー"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING -> "ストレッチ"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_FREESTYLE -> "自由形"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_OPEN_WATER -> "オープンウォータースイミング"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_POOL -> "水泳"
  ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING -> "ウォーキング"
  else -> "その他"
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
