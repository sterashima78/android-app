package dev.terashima.yomitorirss.feature.health.data

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectPermissionsTest {
  @Test
  fun `ヘルスの読取権限には活動消費カロリーを含める`() {
    assertEquals(
      setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
      ),
      HealthConnectHealthRepository.READ_PERMISSIONS,
    )
  }

  @Test
  fun `ワークアウト書出し権限は運動セッションだけに限定する`() {
    assertEquals(
      setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class)),
      HealthConnectHealthRepository.WRITE_PERMISSIONS,
    )
  }
}
