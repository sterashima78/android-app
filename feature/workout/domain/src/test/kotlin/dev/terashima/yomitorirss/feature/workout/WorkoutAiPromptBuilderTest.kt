package dev.terashima.yomitorirss.feature.workout

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAiPromptBuilderTest {
  private val today = LocalDate.of(2026, 8, 27)

  @Test
  fun `メニュー提案には直近14日と方針とプリセットとメモを含める`() {
    val snapshot = snapshotWithHistory()
    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.MENU_SUGGESTION,
      snapshot = snapshot,
      settings = WorkoutAiSettings(workoutPolicy = "継続を優先する"),
      memos = mapOf(
        "2026-08-27" to "今日は少し疲れている",
        "2026-08-14" to "調子が良い",
        "2026-08-13" to "14日より前",
      ),
      today = today,
    )

    assertTrue(prompt.contains("継続を優先する"))
    assertTrue(prompt.contains("プリセットメニュー"))
    assertTrue(prompt.contains("基本メニュー"))
    assertTrue(prompt.contains("腕立て伏せ: 3セット 回"))
    assertTrue(prompt.contains("今日は少し疲れている"))
    assertTrue(prompt.contains("2026-08-14"))
    assertFalse(prompt.contains("2026-08-13"))
    assertTrue(prompt.contains("JSONだけで返してください"))
    assertTrue(prompt.contains("\"sets\":[10,10,8]"))
  }

  @Test
  fun `セットの実際に記録した強度とフォームと負荷をAIへ渡す`() {
    val set = workoutSet("details", "斜め懸垂", 10).copy(
      rpe = 8,
      formQuality = WorkoutFormQuality.UNSTABLE,
      load = WorkoutLoad(WorkoutLoadKind.BODY_ANGLE, "中程度の傾斜"),
      restSeconds = 90,
    )
    val snapshot = snapshotWithHistory().copy(
      today = WorkoutDay(date = today.toString(), sets = listOf(set)),
    )

    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.POST_WORKOUT_REVIEW,
      snapshot = snapshot,
      settings = WorkoutAiSettings(),
      memos = emptyMap(),
      today = today,
    )

    assertTrue(prompt.contains("RPE 8/10"))
    assertTrue(prompt.contains("フォーム: 途中から崩れた"))
    assertTrue(prompt.contains("身体の角度: 中程度の傾斜"))
    assertTrue(prompt.contains("直前の休憩: 90秒"))
    assertTrue(prompt.contains("負荷条件が異なるセットの回数は単純比較しない"))
  }

  @Test
  fun `完了後レビューには進行中の当日実績を含める`() {
    val snapshot = snapshotWithHistory().copy(
      today = WorkoutDay(
        date = "2026-08-27",
        sets = listOf(workoutSet("today", "腕立て伏せ", 12)),
      ),
    )

    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.POST_WORKOUT_REVIEW,
      snapshot = snapshot,
      settings = WorkoutAiSettings(),
      memos = mapOf("2026-08-27" to "最後のセットがきつかった"),
      today = today,
    )

    assertTrue(prompt.contains("腕立て伏せ: 12回"))
    assertTrue(prompt.contains("最後のセットがきつかった"))
    assertTrue(prompt.contains("予定との差分"))
    assertTrue(prompt.contains("判断できない点"))
  }

  @Test
  fun `当日完了済み履歴のセットを今日の記録済みセットとして含める`() {
    val snapshot = snapshotWithHistory().copy(
      today = WorkoutDay(date = "2026-08-27"),
      history = listOf(
        workoutHistory("2026-08-27", workoutSet("completed-today", "プランク", 60, WorkoutUnit.SECONDS)),
        workoutHistory("2026-08-14"),
      ),
    )

    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.POST_WORKOUT_REVIEW,
      snapshot = snapshot,
      settings = WorkoutAiSettings(),
      memos = emptyMap(),
      today = today,
    )

    val todaySection = prompt.substringAfter("## 今日 2026-08-27")
    assertTrue(todaySection.contains("記録済みセット:"))
    assertTrue(todaySection.contains("プランク: 60秒"))
    assertFalse(todaySection.contains("記録済みセット: なし"))
    assertFalse(prompt.substringBefore("## 今日 2026-08-27").contains("### 2026-08-27"))
  }

  @Test
  fun `完了後レビューは保存された当日メニューだけを予定として扱う`() {
    val base = newWorkoutSnapshot(today.toString())
    val pushUp = base.exercises.first { it.id == "push-up" }
    val plannedMenu = WorkoutMenu(
      id = "light-day",
      name = "軽め",
      items = listOf(WorkoutMenuItem(pushUp.id, targetSets = 2, targets = listOf(8, 8))),
    )
    val snapshot = base.copy(
      history = listOf(
        WorkoutHistory(
          id = "today",
          date = today.toString(),
          startedAt = null,
          finishedAt = "2026-08-27T08:30:00+09:00",
          sets = listOf(workoutSet("push", "腕立て伏せ", 8)),
          menu = plannedMenu,
        ),
      ),
      today = WorkoutDay(date = today.toString()),
    )

    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.POST_WORKOUT_REVIEW,
      snapshot = snapshot,
      settings = WorkoutAiSettings(),
      memos = emptyMap(),
      today = today,
    )

    val todaySection = prompt.substringAfter("## 今日 2026-08-27").substringBefore("## 依頼")
    assertTrue(todaySection.contains("軽め"))
    assertTrue(todaySection.contains("腕立て伏せ: [8, 8] 回"))
    assertFalse(todaySection.contains("ランジ"))
    assertTrue(prompt.contains("他のプリセットにあるだけの種目を未実施扱いしない"))
  }

  @Test
  fun `メニュー提案は直近レビューを二次情報として含める`() {
    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.MENU_SUGGESTION,
      snapshot = snapshotWithHistory(),
      settings = WorkoutAiSettings(),
      memos = emptyMap(),
      reviews = listOf(
        WorkoutAiReview(
          date = "2026-08-26",
          generatedAt = "2026-08-26T09:00:00+09:00",
          provider = WorkoutAiProvider.LOCAL,
          content = "次回は回数を維持する",
        ),
        WorkoutAiReview(
          date = "2026-08-13",
          generatedAt = "2026-08-13T09:00:00+09:00",
          provider = WorkoutAiProvider.LOCAL,
          content = "範囲外レビュー",
        ),
      ),
      today = today,
    )

    assertTrue(prompt.contains("過去のAIレビュー（参考情報）"))
    assertTrue(prompt.contains("二次情報"))
    assertTrue(prompt.contains("次回は回数を維持する"))
    assertFalse(prompt.contains("範囲外レビュー"))
  }

  @Test
  fun `完了後レビューには過去AIレビューを混入させない`() {
    val prompt = WorkoutAiPromptBuilder.build(
      type = WorkoutAiRequestType.POST_WORKOUT_REVIEW,
      snapshot = snapshotWithHistory(),
      settings = WorkoutAiSettings(),
      memos = emptyMap(),
      reviews = listOf(
        WorkoutAiReview(
          date = "2026-08-26",
          generatedAt = "2026-08-26T09:00:00+09:00",
          provider = WorkoutAiProvider.LOCAL,
          content = "過去レビュー本文",
        ),
      ),
      today = today,
    )

    assertFalse(prompt.contains("過去レビュー本文"))
  }

  @Test
  fun `recentDates は今日と直近14日だけを返す`() {
    val dates = WorkoutAiPromptBuilder.recentDates(snapshotWithHistory(), today)

    assertTrue("2026-08-27" in dates)
    assertTrue("2026-08-14" in dates)
    assertFalse("2026-08-13" in dates)
  }

  private fun snapshotWithHistory(): WorkoutSnapshot = newWorkoutSnapshot(today.toString()).copy(
    history = listOf(
      workoutHistory("2026-08-14"),
      workoutHistory("2026-08-13"),
    ),
  )

  private fun workoutHistory(
    date: String,
    set: WorkoutSet = workoutSet(date, "ランジ", 10),
  ) = WorkoutHistory(
    id = date,
    date = date,
    startedAt = null,
    finishedAt = "${date}T08:00:00+09:00",
    sets = listOf(set),
  )

  private fun workoutSet(
    id: String,
    name: String,
    amount: Int,
    unit: WorkoutUnit = WorkoutUnit.REPS,
  ) = WorkoutSet(
    id = id,
    exerciseId = "exercise-$id",
    exerciseName = name,
    unit = unit,
    type = if (unit == WorkoutUnit.SECONDS) WorkoutExerciseType.TIMED else WorkoutExerciseType.REPS,
    amount = amount,
    recordedAt = "2026-08-27T08:00:00+09:00",
  )
}
