package dev.terashima.yomitorirss.feature.calendar.data

import dev.terashima.yomitorirss.feature.calendar.CalendarEvent
import dev.terashima.yomitorirss.feature.calendar.CalendarEventKind
import dev.terashima.yomitorirss.feature.calendar.CalendarEventSource
import dev.terashima.yomitorirss.feature.calendar.CalendarEventTime
import dev.terashima.yomitorirss.feature.task.TaskItem
import dev.terashima.yomitorirss.feature.workout.WorkoutDay
import dev.terashima.yomitorirss.feature.workout.WorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import dev.terashima.yomitorirss.feature.workout.WorkoutUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventMappingTest {
  @Test
  fun `期限のあるタスクを終日イベントへ変換する`() {
    val dueDate = LocalDate.of(2026, 8, 19)
    val task = TaskItem(
      id = "task-1",
      title = "確認する",
      parentId = null,
      dueDate = dueDate,
      completedAt = null,
      createdAt = Instant.EPOCH,
      sortOrder = 0,
    )

    val events = taskCalendarEvents(
      tasks = listOf(task),
      fromInclusive = dueDate.withDayOfMonth(1),
      untilExclusive = dueDate.plusMonths(1).withDayOfMonth(1),
    )

    assertEquals(1, events.size)
    assertEquals(CalendarEventSource.TASK, events.single().source)
    assertEquals(CalendarEventKind.DEADLINE, events.single().kind)
    assertEquals(
      CalendarEventTime.AllDay(dueDate, dueDate.plusDays(1)),
      events.single().time,
    )
  }

  @Test
  fun `期間外または期限なしのタスクはカレンダーへ出さない`() {
    val from = LocalDate.of(2026, 8, 1)
    val tasks = listOf(
      TaskItem("without-date", "期限なし", parentId = null, dueDate = null, completedAt = null, createdAt = Instant.EPOCH, sortOrder = 0),
      TaskItem("outside", "来月", parentId = null, dueDate = LocalDate.of(2026, 9, 1), completedAt = null, createdAt = Instant.EPOCH, sortOrder = 1),
    )

    assertTrue(taskCalendarEvents(tasks, from, from.plusMonths(1)).isEmpty())
  }

  @Test
  fun `ワークアウト実績を活動イベントへ変換する`() {
    val date = LocalDate.of(2026, 8, 19)
    val day = WorkoutDay(
      date = date.toString(),
      startedAt = "2026-08-19T10:00:00+09:00",
      sets = listOf(
        WorkoutSet(
          id = "set-1",
          exerciseId = "push-up",
          exerciseName = "腕立て伏せ",
          unit = WorkoutUnit.REPS,
          type = WorkoutExerciseType.REPS,
          amount = 20,
          recordedAt = "2026-08-19T10:05:00+09:00",
        ),
      ),
    )

    val events = workoutCalendarEvents(
      days = listOf(day),
      fromInclusive = date.withDayOfMonth(1),
      untilExclusive = date.plusMonths(1).withDayOfMonth(1),
    )

    assertEquals(1, events.size)
    assertEquals(CalendarEventSource.WORKOUT, events.single().source)
    assertEquals(CalendarEventKind.ACTIVITY, events.single().kind)
    assertEquals(CalendarEventTime.AllDay(date, date.plusDays(1)), events.single().time)
  }

  @Test
  fun `終日イベントは要求期間と重なる場合だけ含める`() {
    val august = LocalDate.of(2026, 8, 1)
    val event = CalendarEvent(
      id = "device:1",
      source = CalendarEventSource.DEVICE_CALENDAR,
      kind = CalendarEventKind.SCHEDULE,
      title = "翌月の予定",
      time = CalendarEventTime.AllDay(
        startDate = LocalDate.of(2026, 9, 1),
        endDateExclusive = LocalDate.of(2026, 9, 2),
      ),
    )

    assertFalse(event.overlapsDateRange(august, august.plusMonths(1)))
  }

  @Test
  fun `時刻イベントはローカル日付範囲との重なりで判定する`() {
    val tokyo = ZoneId.of("Asia/Tokyo")
    val day = LocalDate.of(2026, 8, 19)
    val event = CalendarEvent(
      id = "device:2",
      source = CalendarEventSource.DEVICE_CALENDAR,
      kind = CalendarEventKind.SCHEDULE,
      title = "深夜の予定",
      time = CalendarEventTime.Timed(
        start = Instant.parse("2026-08-19T14:30:00Z"),
        endExclusive = Instant.parse("2026-08-19T16:30:00Z"),
      ),
    )

    assertTrue(event.overlapsDateRange(day, day.plusDays(1), tokyo))
    assertTrue(event.overlapsDateRange(day.plusDays(1), day.plusDays(2), tokyo))
  }
}
