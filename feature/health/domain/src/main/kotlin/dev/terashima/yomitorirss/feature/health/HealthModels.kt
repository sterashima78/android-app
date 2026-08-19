package dev.terashima.yomitorirss.feature.health

import java.time.Instant

enum class HealthAvailability {
  AVAILABLE,
  UNAVAILABLE,
  PROVIDER_UPDATE_REQUIRED,
}

data class HealthOverview(
  val steps: Long? = null,
  val exerciseMinutes: Long? = null,
  val averageHeartRateBpm: Long? = null,
  val sleepMinutes: Long? = null,
  val averageWeightKg: Double? = null,
)

interface HealthRepository {
  fun availability(): HealthAvailability

  suspend fun hasRequiredPermissions(): Boolean

  suspend fun readOverview(startTime: Instant, endTime: Instant): HealthOverview
}
