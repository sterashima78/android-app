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

    val deduplicated = deduplicateExerciseSessions(
      listOf(
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, plain, "example.tracker.a"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, detailed, "example.tracker.b"),
      ),
    )

    assertEquals(listOf(detailed), deduplicated)
  }

  @Test
  fun `提供元ごとに時刻と種別が少し異なる同一運動は一件にまとめる`() {
    val detailedWorkout = session("2026-08-21T09:23:00Z", "2026-08-21T09:46:31Z").copy(
      exerciseName = "その他の運動",
      title = "ワークアウト",
      segments = listOf(
        segment("2026-08-21T09:23:00Z", "2026-08-21T09:30:00Z", "クランチ"),
        segment("2026-08-21T09:31:00Z", "2026-08-21T09:38:00Z", "ランジ"),
        segment("2026-08-21T09:39:00Z", "2026-08-21T09:46:31Z", "プランク"),
      ),
    )
    val longWalking = session("2026-08-21T09:26:00Z", "2026-08-21T09:48:00Z").copy(
      exerciseName = "ウォーキング",
    )
    val shortWalking = session("2026-08-21T09:26:00Z", "2026-08-21T09:40:00Z").copy(
      exerciseName = "ウォーキング",
    )

    val deduplicated = deduplicateExerciseSessions(
      listOf(
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, detailedWorkout, "example.workout"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, longWalking, "example.tracker.a"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, shortWalking, "example.tracker.b"),
      ),
    )

    assertEquals(listOf(detailedWorkout), deduplicated)
  }

  @Test
  fun `同じ提供元の重なったセッションは別の運動として保持する`() {
    val first = session("2026-08-20T08:50:00Z", "2026-08-20T09:17:00Z").copy(
      exerciseName = "ウォーキング",
    )
    val shifted = session("2026-08-20T08:50:01Z", "2026-08-20T09:17:00Z").copy(
      exerciseName = "ウォーキング",
    )

    val deduplicated = deduplicateExerciseSessions(
      listOf(
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, first, "example.tracker"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, shifted, "example.tracker"),
      ),
    )

    assertEquals(2, deduplicated.size)
  }

  @Test
  fun `異なる提供元でも重なりが小さいセッションは別の運動として保持する`() {
    val first = session("2026-08-20T08:00:00Z", "2026-08-20T08:30:00Z")
    val second = session("2026-08-20T08:20:00Z", "2026-08-20T08:50:00Z")

    val deduplicated = deduplicateExerciseSessions(
      listOf(
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, first, "example.tracker.a"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, second, "example.tracker.b"),
      ),
    )

    assertEquals(2, deduplicated.size)
  }

  @Test
  fun `長時間セッションに半分の長さの短時間セッションが含まれるだけでは統合しない`() {
    val longSession = session("2026-08-20T08:00:00Z", "2026-08-20T09:00:00Z")
    val shortSession = session("2026-08-20T08:15:00Z", "2026-08-20T08:45:00Z")

    val deduplicated = deduplicateExerciseSessions(
      listOf(
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, longSession, "example.tracker.a"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, shortSession, "example.tracker.b"),
      ),
    )

    assertEquals(2, deduplicated.size)
  }

  @Test
  fun `詳細セッションの内訳と一致する別提供元の単独セッションは統合する`() {
    val detailedWorkout = session("2026-01-15T10:00:00Z", "2026-01-15T11:00:00Z").copy(
      exerciseName = "ウォーキング",
      title = "ワークアウト",
      segments = listOf(
        segment("2026-01-15T10:00:00Z", "2026-01-15T10:15:00Z", "ウォーキング"),
        segment("2026-01-15T10:20:00Z", "2026-01-15T10:35:00Z", "ウォーキング"),
        segment("2026-01-15T10:40:00Z", "2026-01-15T10:55:00Z", "ウォーキング"),
      ),
    )
    val firstStandalone = session("2026-01-15T10:02:00Z", "2026-01-15T10:14:00Z").copy(
      exerciseName = "ウォーキング",
    )
    val secondStandalone = session("2026-01-15T10:36:00Z", "2026-01-15T10:56:00Z").copy(
      exerciseName = "ウォーキング",
    )

    val deduplicated = deduplicateExerciseSessions(
      listOf(
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, detailedWorkout, "example.workout"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, firstStandalone, "example.tracker.a"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, secondStandalone, "example.tracker.b"),
      ),
    )

    assertEquals(listOf(detailedWorkout), deduplicated)
  }

  @Test
  fun `詳細セッションの内訳と十分に一致しない単独セッションは保持する`() {
    val detailedWorkout = session("2026-01-15T10:00:00Z", "2026-01-15T11:00:00Z").copy(
      exerciseName = "ウォーキング",
      title = "ワークアウト",
      segments = listOf(
        segment("2026-01-15T10:00:00Z", "2026-01-15T10:10:00Z", "ウォーキング"),
      ),
    )
    val standalone = session("2026-01-15T10:05:00Z", "2026-01-15T10:30:00Z").copy(
      exerciseName = "ウォーキング",
    )

    val deduplicated = deduplicateExerciseSessions(
      listOf(
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, detailedWorkout, "example.workout"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, standalone, "example.tracker"),
      ),
    )

    assertEquals(2, deduplicated.size)
  }

  @Test
  fun `同一提供元では同一時刻でもHealth Connect種別が異なるセッションを保持する`() {
    val walking = session("2026-08-20T09:38:00Z", "2026-08-20T09:59:00Z").copy(
      exerciseName = "ウォーキング",
    )
    val running = walking.copy(exerciseName = "ランニング")

    val deduplicated = deduplicateExerciseSessions(
      listOf(
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, walking, "example.tracker"),
        candidate(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, running, "example.tracker"),
      ),
    )

    assertEquals(2, deduplicated.size)
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

  private fun candidate(
    exerciseType: Int,
    summary: HealthExerciseSessionSummary,
    dataOriginPackageName: String,
  ): ExerciseSessionCandidate = ExerciseSessionCandidate(
    exerciseType = exerciseType,
    summary = summary,
    dataOriginPackageName = dataOriginPackageName,
  )

  private fun session(start: String, end: String): HealthExerciseSessionSummary = HealthExerciseSessionSummary(
    startTime = Instant.parse(start),
    endTime = Instant.parse(end),
    exerciseName = "運動",
  )

  private fun segment(start: String, end: String, name: String): HealthExerciseSegmentSummary =
    HealthExerciseSegmentSummary(
      startTime = Instant.parse(start),
      endTime = Instant.parse(end),
      exerciseName = name,
    )
}
