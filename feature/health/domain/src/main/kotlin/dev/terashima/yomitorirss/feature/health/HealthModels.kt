package dev.terashima.yomitorirss.feature.health

import java.time.Instant
import java.time.LocalDate

enum class HealthAvailability {
  AVAILABLE,
  UNAVAILABLE,
  PROVIDER_UPDATE_REQUIRED,
}

enum class HealthHistoryAccess {
  AVAILABLE,
  PERMISSION_REQUIRED,
  UNSUPPORTED,
}

data class BodyFatMeasurement(
  val time: Instant,
  val percentage: Double,
)

data class DailyHealthSummary(
  val date: LocalDate,
  val steps: Long? = null,
  val activeCaloriesKcal: Double? = null,
  val exerciseMinutes: Long? = null,
  val averageHeartRateBpm: Long? = null,
  val sleepMinutes: Long? = null,
  val averageWeightKg: Double? = null,
)

data class DailyNutritionIntake(
  val date: LocalDate,
  val energyKcal: Double = 0.0,
  val proteinGrams: Double = 0.0,
  val fatGrams: Double = 0.0,
  val carbohydrateGrams: Double = 0.0,
)

data class HealthExerciseSegmentSummary(
  val startTime: Instant,
  val endTime: Instant,
  val exerciseName: String,
  val repetitions: Int = 0,
)

data class HealthExerciseSessionSummary(
  val startTime: Instant,
  val endTime: Instant,
  val exerciseName: String,
  val title: String? = null,
  val notes: String? = null,
  val segments: List<HealthExerciseSegmentSummary> = emptyList(),
)

data class NutritionReferenceRange(
  val min: Double,
  val max: Double,
) {
  init {
    require(min >= 0.0) { "Nutrition reference minimum must be non-negative" }
    require(max >= min) { "Nutrition reference maximum must be greater than or equal to minimum" }
  }
}

data class NutritionReferenceProfile(
  val label: String,
  val energyKcal: NutritionReferenceRange,
  val proteinGrams: NutritionReferenceRange,
  val fatGrams: NutritionReferenceRange,
  val carbohydrateGrams: NutritionReferenceRange,
  val proteinRecommendedGrams: Double,
)

object AdultMaleNutritionReference {
  const val REFERENCE_AGE_LABEL = "30〜49歳男性・身体活動ふつう"
  const val STANDARD_ENERGY_KCAL = 2750.0
  const val WEIGHT_LOSS_ENERGY_DEFICIT_KCAL = 500.0
  const val WEIGHT_LOSS_ENERGY_KCAL = STANDARD_ENERGY_KCAL - WEIGHT_LOSS_ENERGY_DEFICIT_KCAL
  const val PROTEIN_RECOMMENDED_GRAMS = 65.0

  val standard: NutritionReferenceProfile = profile(
    label = "標準目安",
    energyKcal = STANDARD_ENERGY_KCAL,
  )

  val weightLoss: NutritionReferenceProfile = profile(
    label = "減量参考",
    energyKcal = WEIGHT_LOSS_ENERGY_KCAL,
  )

  private fun profile(label: String, energyKcal: Double): NutritionReferenceProfile =
    NutritionReferenceProfile(
      label = label,
      energyKcal = NutritionReferenceRange(energyKcal, energyKcal),
      proteinGrams = energyPercentRangeToGrams(
        energyKcal = energyKcal,
        minPercent = 13.0,
        maxPercent = 20.0,
        kcalPerGram = 4.0,
      ),
      fatGrams = energyPercentRangeToGrams(
        energyKcal = energyKcal,
        minPercent = 20.0,
        maxPercent = 30.0,
        kcalPerGram = 9.0,
      ),
      carbohydrateGrams = energyPercentRangeToGrams(
        energyKcal = energyKcal,
        minPercent = 50.0,
        maxPercent = 65.0,
        kcalPerGram = 4.0,
      ),
      proteinRecommendedGrams = PROTEIN_RECOMMENDED_GRAMS,
    )

  private fun energyPercentRangeToGrams(
    energyKcal: Double,
    minPercent: Double,
    maxPercent: Double,
    kcalPerGram: Double,
  ): NutritionReferenceRange = NutritionReferenceRange(
    min = energyKcal * (minPercent / 100.0) / kcalPerGram,
    max = energyKcal * (maxPercent / 100.0) / kcalPerGram,
  )
}

data class HealthOverview(
  val steps: Long? = null,
  val activeCaloriesKcal: Double? = null,
  val exerciseMinutes: Long? = null,
  val averageHeartRateBpm: Long? = null,
  val sleepMinutes: Long? = null,
  val averageWeightKg: Double? = null,
  val bodyFatMeasurements: List<BodyFatMeasurement> = emptyList(),
  val nutritionDailyIntakes: List<DailyNutritionIntake> = emptyList(),
  val exerciseSessions: List<HealthExerciseSessionSummary> = emptyList(),
  val dailySummaries: List<DailyHealthSummary> = emptyList(),
)

interface HealthRepository {
  fun availability(): HealthAvailability

  suspend fun hasRequiredPermissions(): Boolean

  suspend fun historyAccess(): HealthHistoryAccess

  suspend fun readOverview(startTime: Instant, endTime: Instant): HealthOverview
}
