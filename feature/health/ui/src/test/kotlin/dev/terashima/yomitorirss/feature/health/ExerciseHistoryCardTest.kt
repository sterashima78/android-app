package dev.terashima.yomitorirss.feature.health

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseHistoryCardTest {
  @Test
  fun `同日の運動時間帯は開始日に続けて時刻を表示する`() {
    val result = formatExerciseSessionTimeRange(
      startTime = Instant.parse("2026-08-20T10:00:00Z"),
      endTime = Instant.parse("2026-08-20T10:32:00Z"),
      zoneId = ZoneId.of("Asia/Tokyo"),
    )

    assertEquals("8/20 19:00–19:32", result)
  }

  @Test
  fun `日付をまたぐ運動は終了日も表示する`() {
    val result = formatExerciseSessionTimeRange(
      startTime = Instant.parse("2026-08-20T14:50:00Z"),
      endTime = Instant.parse("2026-08-20T15:10:00Z"),
      zoneId = ZoneId.of("Asia/Tokyo"),
    )

    assertEquals("8/20 23:50–8/21 00:10", result)
  }

  @Test
  fun `運動時間は秒単位の内訳も保持する`() {
    assertEquals(
      "1分5秒",
      formatExerciseDuration(
        Instant.parse("2026-08-20T10:00:00Z"),
        Instant.parse("2026-08-20T10:01:05Z"),
      ),
    )
  }

  @Test
  fun `運動時間帯の活動量をまとめて表示する`() {
    val session = HealthExerciseSessionSummary(
      startTime = Instant.parse("2026-08-20T10:00:00Z"),
      endTime = Instant.parse("2026-08-20T10:32:00Z"),
      exerciseName = "ウォーキング",
      activeCaloriesKcal = 120.4,
      averageHeartRateBpm = 132,
      steps = 1560,
    )

    assertEquals(
      "活動消費 120 kcal  ・  平均心拍 132 bpm  ・  1560 歩",
      formatExerciseActivitySummary(session),
    )
  }

  @Test
  fun `活動量がない運動には補助情報を表示しない`() {
    val session = HealthExerciseSessionSummary(
      startTime = Instant.parse("2026-08-20T10:00:00Z"),
      endTime = Instant.parse("2026-08-20T10:32:00Z"),
      exerciseName = "ストレッチ",
    )

    assertNull(formatExerciseActivitySummary(session))
  }
}
