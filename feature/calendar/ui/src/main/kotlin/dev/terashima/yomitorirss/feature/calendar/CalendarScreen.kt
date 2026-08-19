package dev.terashima.yomitorirss.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
  viewModel: CalendarViewModel,
  calendarPermissionGranted: Boolean,
  onRequestCalendarPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  val selectedEvents = state.events.filter { it.occursOn(state.selectedDate) }

  Column(modifier.fillMaxSize()) {
    MonthHeader(
      month = state.month,
      onPrevious = viewModel::previousMonth,
      onNext = viewModel::nextMonth,
      onToday = viewModel::goToToday,
    )
    MonthGrid(
      month = state.month,
      selectedDate = state.selectedDate,
      events = state.events,
      onSelectDate = viewModel::selectDate,
    )
    HorizontalDivider()
    if (state.loading) {
      LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (!calendarPermissionGranted) {
      CalendarPermissionCard(onRequestCalendarPermission)
    }
    AgendaHeader(state.selectedDate, selectedEvents.size)
    when {
      state.error != null -> ErrorState(state.error.orEmpty(), viewModel::reload)
      state.loading && state.events.isEmpty() -> Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
      selectedEvents.isEmpty() -> Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "この日の予定・期限・実績はありません",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(selectedEvents, key = CalendarEvent::id) { event ->
          CalendarEventRow(event)
          HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
      }
    }
  }
}

@Composable
private fun MonthHeader(
  month: YearMonth,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onToday: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onPrevious) {
      Icon(Icons.Default.ChevronLeft, contentDescription = "前の月")
    }
    Text(
      text = "${month.year}年 ${month.monthValue}月",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onToday) {
      Text("今日")
    }
    IconButton(onClick = onNext) {
      Icon(Icons.Default.ChevronRight, contentDescription = "次の月")
    }
  }
}

@Composable
private fun MonthGrid(
  month: YearMonth,
  selectedDate: LocalDate,
  events: List<CalendarEvent>,
  onSelectDate: (LocalDate) -> Unit,
) {
  val weekDays = listOf("日", "月", "火", "水", "木", "金", "土")
  val firstDay = month.atDay(1)
  val leadingEmptyCells = firstDay.dayOfWeek.value % 7
  val today = LocalDate.now()

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Row(Modifier.fillMaxWidth()) {
      weekDays.forEach { label ->
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier
            .weight(1f)
            .padding(vertical = 4.dp),
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    }

    repeat(6) { week ->
      Row(Modifier.fillMaxWidth()) {
        repeat(7) { dayOfWeek ->
          val cellIndex = week * 7 + dayOfWeek
          val day = cellIndex - leadingEmptyCells + 1
          if (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val dayEvents = events.filter { it.occursOn(date) }
            DayCell(
              date = date,
              selected = date == selectedDate,
              today = date == today,
              events = dayEvents,
              onClick = { onSelectDate(date) },
              modifier = Modifier.weight(1f),
            )
          } else {
            Spacer(
              Modifier
                .weight(1f)
                .height(48.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DayCell(
  date: LocalDate,
  selected: Boolean,
  today: Boolean,
  events: List<CalendarEvent>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val background = if (selected) {
    MaterialTheme.colorScheme.secondaryContainer
  } else {
    Color.Transparent
  }
  Column(
    modifier = modifier
      .height(48.dp)
      .padding(2.dp)
      .background(background, RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = date.dayOfMonth.toString(),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
      color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.height(8.dp),
    ) {
      events.take(3).forEach { event ->
        Box(
          Modifier
            .size(5.dp)
            .background(calendarEventColor(event), CircleShape),
        )
      }
    }
  }
}

@Composable
private fun CalendarPermissionCard(onRequestPermission: () -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Text("端末のカレンダーを表示", fontWeight = FontWeight.SemiBold)
        Text(
          "Google カレンダーなど端末に同期済みの予定を読み取ります。タスクとワークアウトは権限なしでも表示されます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(onClick = onRequestPermission) {
        Text("許可")
      }
    }
  }
}

@Composable
private fun AgendaHeader(date: LocalDate, count: Int) {
  val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "${date.monthValue}月${date.dayOfMonth}日 ($dayOfWeek)",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = "$count 件",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun CalendarEventRow(event: CalendarEvent) {
  val color = calendarEventColor(event)
  ListItem(
    leadingContent = {
      Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = CircleShape,
      ) {
        Box(
          modifier = Modifier.size(44.dp),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = when (event.source) {
              CalendarEventSource.DEVICE_CALENDAR -> Icons.Default.Event
              CalendarEventSource.TASK -> Icons.Default.Checklist
              CalendarEventSource.WORKOUT -> Icons.Default.FitnessCenter
            },
            contentDescription = null,
          )
        }
      }
    },
    headlineContent = {
      Text(event.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    },
    overlineContent = {
      Text(
        text = buildString {
          append(calendarEventTimeLabel(event))
          append(" · ")
          append(eventSourceLabel(event))
        },
        color = color,
      )
    },
    supportingContent = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        event.description?.let {
          Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        event.location?.let { location ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.Place,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(location, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    },
  )
}

@Composable
private fun calendarEventColor(event: CalendarEvent): Color {
  if (event.source == CalendarEventSource.DEVICE_CALENDAR) {
    event.sourceMetadata?.sourceColorArgb?.let { return Color(it) }
  }
  return when (event.kind) {
    CalendarEventKind.SCHEDULE -> MaterialTheme.colorScheme.primary
    CalendarEventKind.DEADLINE -> MaterialTheme.colorScheme.tertiary
    CalendarEventKind.ACTIVITY -> MaterialTheme.colorScheme.secondary
  }
}

private fun eventSourceLabel(event: CalendarEvent): String = when (event.source) {
  CalendarEventSource.DEVICE_CALENDAR -> event.sourceMetadata?.sourceName ?: "端末カレンダー"
  CalendarEventSource.TASK -> "タスク"
  CalendarEventSource.WORKOUT -> "ワークアウト"
}

private fun calendarEventTimeLabel(event: CalendarEvent): String = when (val time = event.time) {
  is CalendarEventTime.AllDay -> when (event.kind) {
    CalendarEventKind.DEADLINE -> "期限"
    CalendarEventKind.ACTIVITY -> "実績"
    CalendarEventKind.SCHEDULE -> "終日"
  }
  is CalendarEventTime.Timed -> DateTimeFormatter
    .ofPattern("H:mm")
    .withZone(ZoneId.systemDefault())
    .format(time.start)
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(message, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(12.dp))
    Button(onClick = onRetry) {
      Text("再読み込み")
    }
  }
}
