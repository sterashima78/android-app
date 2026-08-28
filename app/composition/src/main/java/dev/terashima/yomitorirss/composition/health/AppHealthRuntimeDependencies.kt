package dev.terashima.yomitorirss.composition.health

import android.app.Application
import dev.terashima.yomitorirss.feature.health.data.HealthConnectHealthRepository

/** Application-scope Health Connect repository wiring. */
internal class AppHealthRuntimeDependencies(
  private val application: Application,
) {
  val healthRepository: HealthConnectHealthRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    HealthConnectHealthRepository(application)
  }
}
