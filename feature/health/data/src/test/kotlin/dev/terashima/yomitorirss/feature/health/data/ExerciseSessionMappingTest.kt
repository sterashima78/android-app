package dev.terashima.yomitorirss.feature.health.data

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import dev.terashima.yomitorirss.feature.health.HealthExerciseSegmentSummary
import dev.terashima.yomitorirss.feature.health.HealthExerciseSessionSummary
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseSessionMappingTest {
  @Test
  fun `運動セッションがない場合は運動時間を未取得として扱う`() {
    assertNull(totalExerciseMinutes(emptyList()))
  }

  @Test
  fun `運動時間はセッションごとに丸めず合計してから分へ変換する`() {
    val first = session("2026-08-20T10:00:00Z", "2026-08-20T10:00:30Z")
    val second = session("2026-08-20T11:00:00Z", "2026-08-20T11:00:40Z")

    assertEquals(1L, totalExerciseMinutes(listOf(first, second)))
  }

  @Test
  fun `同一時刻と種別の重複セッションは詳細情報が多い一件にまとめる`() {
    val plain = session("2026-08-20T09:38:00Z", "2026-08-20T09:59:00Z").copy(
      exerciseName = "ウォーキング",
    )
    val detailed = plain.copy(
      title = "朝のウォーキング",
      notes = "テスト用の架空データ",
      segments = listOf(
        HealthExerciseSegmentSummary(
          startTime = Instant.parse("2026-08-20T09:38:00Z"),
          endTime = Instant.parse("2026-08-20T09:59:00Z"),
          exerciseName = "ウォーキング",
        ),
      ),
    )

    val deduplicated = deduplicateExerciseSessions(listOf(plain, detailed))

    assertEquals(listOf(detailed), deduplicated)
    assertEquals(21L, totalExerciseMinutes(deduplicated))
  }

  @Test
  fun `同一時刻でも種別が異なるセッションは別の運動として保持する`() {
    val walking = session("2026-08-20T09:38:00Z", "2026-08-20T09:59:00Z").copy(
      exerciseName = "ウォーキング",
    )
    val running = walking.copy(exerciseName = "ランニング")

    assertEquals(2, deduplicateExerciseSessions(listOf(walking, running)).size)
  }

  @Test
  fun `主要な運動種別を表示名へ変換する`() {
    assertEquals("ウォーキング", exerciseSessionName(ExerciseSessionRecord.EXERCISE_TYPE_WALKING))
    assertEquals("ランニング", exerciseSessionName(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
    assertEquals("筋力トレーニング", exerciseSessionName(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING))
    assertEquals("その他の運動", exerciseSessionName(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT))
  }

  @Test
  fun `主要なセグメント種別を表示名へ変換する`() {
    assertEquals("クランチ", exerciseSegmentName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH))
    assertEquals("ランジ", exerciseSegmentName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE))
    assertEquals("プランク", exerciseSegmentName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK))
    assertEquals("階段昇降", exerciseSegmentName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING))
  }

  private fun session(start: String, end: String): HealthExerciseSessionSummary = HealthExerciseSessionSummary(
    startTime = Instant.parse(start),
    endTime = Instant.parse(end),
    exerciseName = "運動",
  )
}
