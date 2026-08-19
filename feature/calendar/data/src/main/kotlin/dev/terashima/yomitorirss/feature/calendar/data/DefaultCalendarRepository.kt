package dev.terashima.yomitorirss.feature.calendar.data

import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import dev.terashima.yomitorirss.feature.calendar.CalendarEvent
import dev.terashima.yomitorirss.feature.calendar.CalendarEventKind
import dev.terashima.yomitorirss.feature.calendar.CalendarEventSource
import dev.terashima.yomitorirss.feature.calendar.CalendarEventSourceMetadata
import dev.terashima.yomitorirss.feature.calendar.CalendarEventTime
import dev.terashima.yomitorirss.feature.calendar.CalendarRepository
import dev.terashima.yomitorirss.feature.task.TaskItem
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.workout.WorkoutDay
import dev.terashima.yomitorirss.feature.workout.WorkoutRepository
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultCalendarRepository(
  context: Context,
  private val taskRepository: TaskRepository,
  private val workoutRepository: WorkoutRepository,
) : CalendarRepository {
  private val deviceCalendar = AndroidCalendarEventSource(context.applicationContext.contentResolver)

  override suspend fun events(
    fromInclusive: LocalDate,
    untilExclusive: LocalDate,
  ): List<CalendarEvent> = withContext(Dispatchers.IO) {
    require(fromInclusive < untilExclusive) { "Calendar range must not be empty" }

    buildList {
      addAll(deviceCalendar.events(fromInclusive, untilExclusive))
      addAll(taskCalendarEvents(taskRepository.listTasks(), fromInclusive, untilExclusive))

      val workout = workoutRepository.load()
      addAll(
        workoutCalendarEvents(
          days = buildList {
            addAll(workout.history.map { history -> WorkoutDay(history.date, history.startedAt, history.sets) })
            if (workout.today.sets.isNotEmpty()) add(workout.today)
          },
          fromInclusive = fromInclusive,
          untilExclusive = untilExclusive,
        ),
      )
    }.sortedWith(compareBy(::calendarEventSortKey, CalendarEvent::title))
  }
}

internal fun taskCalendarEvents(
  tasks: List<TaskItem>,
  fromInclusive: LocalDate,
  untilExclusive: LocalDate,
): List<CalendarEvent> = tasks.mapNotNull { task ->
  val dueDate = task.dueDate ?: return@mapNotNull null
  if (dueDate < fromInclusive || dueDate >= untilExclusive) return@mapNotNull null

  CalendarEvent(
    id = "task:${task.id}",
    source = CalendarEventSource.TASK,
    kind = CalendarEventKind.DEADLINE,
    title = task.title,
    description = buildList {
      if (task.completed) add("完了")
      task.description.takeIf(String::isNotBlank)?.let(::add)
    }.joinToString(" · ").ifBlank { null },
    time = CalendarEventTime.AllDay(
      startDate = dueDate,
      endDateExclusive = dueDate.plusDays(1),
    ),
  )
}

internal fun workoutCalendarEvents(
  days: List<WorkoutDay>,
  fromInclusive: LocalDate,
  untilExclusive: LocalDate,
): List<CalendarEvent> = days.mapNotNull { day ->
  val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
  if (date == null || date < fromInclusive || date >= untilExclusive || day.sets.isEmpty()) {
    return@mapNotNull null
  }

  CalendarEvent(
    id = "workout:${day.date}:${day.startedAt.orEmpty()}",
    source = CalendarEventSource.WORKOUT,
    kind = CalendarEventKind.ACTIVITY,
    title = "ワークアウト ${day.sets.size}セット",
    description = workoutDescription(day.sets),
    time = CalendarEventTime.AllDay(
      startDate = date,
      endDateExclusive = date.plusDays(1),
    ),
  )
}

private fun workoutDescription(sets: List<WorkoutSet>): String = sets
  .groupBy(WorkoutSet::exerciseName)
  .entries
  .joinToString(" / ") { (name, exerciseSets) -> "$name ${exerciseSets.size}セット" }

private class AndroidCalendarEventSource(
  private val contentResolver: ContentResolver,
) {
  fun events(
    fromInclusive: LocalDate,
    untilExclusive: LocalDate,
  ): List<CalendarEvent> {
    val zoneId = ZoneId.systemDefault()
    val beginMillis = fromInclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val endMillis = untilExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val projection = arrayOf(
      CalendarContract.Instances.EVENT_ID,
      CalendarContract.Instances.BEGIN,
      CalendarContract.Instances.END,
      CalendarContract.Events.ALL_DAY,
      CalendarContract.Events.TITLE,
      CalendarContract.Events.DESCRIPTION,
      CalendarContract.Events.EVENT_LOCATION,
      CalendarContract.Instances.CALENDAR_ID,
      CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
      CalendarContract.Events.DISPLAY_COLOR,
    )

    val cursor = try {
      CalendarContract.Instances.query(
        contentResolver,
        projection,
        beginMillis,
        endMillis,
      )
    } catch (_: SecurityException) {
      return emptyList()
    }

    cursor.use { rows ->
      val eventIdIndex = rows.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
      val beginIndex = rows.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
      val endIndex = rows.getColumnIndexOrThrow(CalendarContract.Instances.END)
      val allDayIndex = rows.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
      val titleIndex = rows.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
      val descriptionIndex = rows.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
      val locationIndex = rows.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
      val calendarIdIndex = rows.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
      val calendarNameIndex = rows.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
      val colorIndex = rows.getColumnIndexOrThrow(CalendarContract.Events.DISPLAY_COLOR)

      return buildList {
        while (rows.moveToNext()) {
          val begin = rows.getLong(beginIndex)
          val end = rows.getLong(endIndex)
          val allDay = rows.getInt(allDayIndex) != 0
          val eventId = rows.getLong(eventIdIndex)
          val calendarId = rows.getLong(calendarIdIndex)
          val time = if (allDay) {
            val startDate = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
            val rawEndDate = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
            CalendarEventTime.AllDay(
              startDate = startDate,
              endDateExclusive = rawEndDate.takeIf { it > startDate } ?: startDate.plusDays(1),
            )
          } else {
            CalendarEventTime.Timed(
              start = Instant.ofEpochMilli(begin),
              endExclusive = end.takeIf { it > begin }?.let(Instant::ofEpochMilli),
            )
          }

          add(
            CalendarEvent(
              id = "device:$calendarId:$eventId:$begin",
              source = CalendarEventSource.DEVICE_CALENDAR,
              kind = CalendarEventKind.SCHEDULE,
              title = rows.getString(titleIndex)?.takeIf(String::isNotBlank) ?: "(無題)",
              description = rows.getString(descriptionIndex)?.takeIf(String::isNotBlank),
              location = rows.getString(locationIndex)?.takeIf(String::isNotBlank),
              time = time,
              sourceMetadata = CalendarEventSourceMetadata(
                sourceId = calendarId.toString(),
                sourceName = rows.getString(calendarNameIndex)?.takeIf(String::isNotBlank),
                sourceColorArgb = if (rows.isNull(colorIndex)) null else rows.getInt(colorIndex),
              ),
            ),
          )
        }
      }
    }
  }
}

private fun calendarEventSortKey(event: CalendarEvent): Long = when (val time = event.time) {
  is CalendarEventTime.Timed -> time.start.toEpochMilli()
  is CalendarEventTime.AllDay -> time.startDate
    .atStartOfDay(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()
}
