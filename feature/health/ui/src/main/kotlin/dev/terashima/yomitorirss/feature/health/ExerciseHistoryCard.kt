package dev.terashima.yomitorirss.feature.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SESSION_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
private val SESSION_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun ExerciseHistoryCard(
  sessions: List<HealthExerciseSessionSummary>,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("運動履歴", style = MaterialTheme.typography.titleMedium)
    Text(
      "活動消費・平均心拍・歩数は、各運動セッションと同じ時間帯の Health Connect データを集計して表示します。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (sessions.isEmpty()) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Text(
          "選択期間に運動記録はありません",
          modifier = Modifier.padding(16.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      sessions.forEachIndexed { index, session ->
        Row(
          modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Column(
            modifier = Modifier.width(20.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            if (index < sessions.lastIndex) {
              Box(
                modifier = Modifier
                  .width(2.dp)
                  .weight(1f)
                  .background(MaterialTheme.colorScheme.outlineVariant),
              )
            }
          }
          ExerciseSessionCard(
            session = session,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun ExerciseSessionCard(
  session: HealthExerciseSessionSummary,
  modifier: Modifier = Modifier,
) {
  var expanded by remember(session.startTime, session.endTime) { mutableStateOf(false) }
  val hasDetails = session.notes != null || session.segments.isNotEmpty()

  Card(
    modifier = modifier.clickable(enabled = hasDetails) { expanded = !expanded },
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            session.title ?: session.exerciseName,
            style = MaterialTheme.typography.titleSmall,
          )
          if (session.title != null && session.title != session.exerciseName) {
            Text(
              session.exerciseName,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Text(
          formatExerciseDuration(session.startTime, session.endTime),
          style = MaterialTheme.typography.labelLarge,
        )
      }

      Text(
        formatExerciseSessionTimeRange(session.startTime, session.endTime),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      formatExerciseActivitySummary(session)?.let { activitySummary ->
        Text(
          activitySummary,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      Text(
        when {
          session.segments.isNotEmpty() -> "内訳 ${session.segments.size}件${if (expanded) " · 閉じる" else " · タップして表示"}"
          session.notes != null -> if (expanded) "詳細を閉じる" else "タップして詳細を表示"
          else -> "内訳データなし"
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (expanded) {
        session.notes?.let { notes ->
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("記録メモ", style = MaterialTheme.typography.labelLarge)
            Text(notes, style = MaterialTheme.typography.bodySmall)
          }
        }
        session.segments.forEach { segment -> ExerciseSegmentRow(segment) }
      }
    }
  }
}

@Composable
private fun ExerciseSegmentRow(segment: HealthExerciseSegmentSummary) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(segment.exerciseName, style = MaterialTheme.typography.bodyMedium)
      Text(
        "${formatExerciseTime(segment.startTime)}–${formatExerciseTime(segment.endTime)} · " +
          formatExerciseDuration(segment.startTime, segment.endTime),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (segment.repetitions > 0) {
      Text(
        "${segment.repetitions}回",
        style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

internal fun formatExerciseActivitySummary(session: HealthExerciseSessionSummary): String? {
  val values = buildList {
    session.activeCaloriesKcal?.let {
      add("活動消費 ${String.format(Locale.getDefault(), "%.0f", it)} kcal")
    }
    session.averageHeartRateBpm?.let { add("平均心拍 $it bpm") }
    session.steps?.let { add("$it 歩") }
  }
  return values.takeIf(List<String>::isNotEmpty)?.joinToString("  ・  ")
}

internal fun formatExerciseSessionTimeRange(
  startTime: Instant,
  endTime: Instant,
  zoneId: ZoneId = ZoneId.systemDefault(),
): String {
  val start = startTime.atZone(zoneId)
  val end = endTime.atZone(zoneId)
  return if (start.toLocalDate() == end.toLocalDate()) {
    "${SESSION_DATE_TIME_FORMATTER.format(start)}–${SESSION_TIME_FORMATTER.format(end)}"
  } else {
    "${SESSION_DATE_TIME_FORMATTER.format(start)}–${SESSION_DATE_TIME_FORMATTER.format(end)}"
  }
}

internal fun formatExerciseTime(
  time: Instant,
  zoneId: ZoneId = ZoneId.systemDefault(),
): String = SESSION_TIME_FORMATTER.format(time.atZone(zoneId))

internal fun formatExerciseDuration(startTime: Instant, endTime: Instant): String {
  val seconds = Duration.between(startTime, endTime).seconds.coerceAtLeast(0)
  val minutes = seconds / 60
  val remainingSeconds = seconds % 60
  return when {
    minutes == 0L -> "${seconds}秒"
    remainingSeconds == 0L -> "${minutes}分"
    else -> "${minutes}分${remainingSeconds}秒"
  }
}
