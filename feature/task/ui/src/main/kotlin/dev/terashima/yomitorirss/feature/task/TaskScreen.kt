@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.task

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class TaskEditorRequest(
  val task: TaskItem? = null,
  val parentId: String? = null,
)

@Composable
fun TaskScreen(
  viewModel: TaskViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  var editorRequest by remember { mutableStateOf<TaskEditorRequest?>(null) }
  var deleteTarget by remember { mutableStateOf<TaskItem?>(null) }
  val rows = taskTreeRows(state.tasks, state.filter, state.expandedIds)

  Box(modifier = modifier.fillMaxSize()) {
    if (!state.initialized) {
      CircularProgressIndicator(Modifier.align(Alignment.Center))
    } else {
      Column(Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TaskFilter.entries.forEach { item ->
            FilterChip(
              selected = state.filter == item,
              onClick = { viewModel.selectFilter(item) },
              label = { Text("${item.label} ${taskCount(state.tasks, item)}") },
            )
          }
        }

        state.error?.let { message ->
          Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
          )
        }

        if (rows.isEmpty()) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
              text = when (state.filter) {
                TaskFilter.UNFINISHED -> "未完了のタスクはありません"
                TaskFilter.COMPLETED -> "完了したタスクはありません"
                TaskFilter.OVERDUE -> "期日超過のタスクはありません"
                TaskFilter.ALL -> "タスクはありません"
              },
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            items(rows.size, key = { rows[it].task.id }) { index ->
              val row = rows[index]
              TaskRow(
                row = row,
                expanded = row.task.id in state.expandedIds,
                onExpandToggle = { viewModel.toggleExpanded(row.task.id) },
                onCompletedChange = { completed -> viewModel.setCompleted(row.task.id, completed) },
                onAddChild = { editorRequest = TaskEditorRequest(parentId = row.task.id) },
                onEdit = { editorRequest = TaskEditorRequest(task = row.task, parentId = row.task.parentId) },
                onDelete = { deleteTarget = row.task },
              )
              HorizontalDivider()
            }
          }
        }
      }
    }

    FloatingActionButton(
      onClick = { editorRequest = TaskEditorRequest() },
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    ) {
      Icon(Icons.Default.Add, contentDescription = "タスクを追加")
    }
  }

  editorRequest?.let { request ->
    TaskEditorDialog(
      request = request,
      onDismiss = { editorRequest = null },
      onSave = { title, description, dueDate ->
        editorRequest = null
        if (request.task == null) {
          viewModel.createTask(title, description, request.parentId, dueDate)
        } else {
          viewModel.updateTask(request.task.id, title, description, dueDate)
        }
      },
    )
  }

  deleteTarget?.let { task ->
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      title = { Text("タスクを削除しますか？") },
      text = {
        Text(
          if (state.tasks.any { it.parentId == task.id }) {
            "「${task.title}」と配下の子タスクをすべて削除します。"
          } else {
            "「${task.title}」を削除します。"
          },
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            deleteTarget = null
            viewModel.deleteTask(task.id)
          },
        ) { Text("削除") }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
      },
    )
  }
}

@Composable
private fun TaskRow(
  row: TaskTreeRow,
  expanded: Boolean,
  onExpandToggle: () -> Unit,
  onCompletedChange: (Boolean) -> Unit,
  onAddChild: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  var menuOpen by remember(row.task.id) { mutableStateOf(false) }
  val task = row.task
  val status = taskStatus(task)
  val statusColor = when (status) {
    TaskStatus.OVERDUE -> MaterialTheme.colorScheme.error
    TaskStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
    TaskStatus.UNFINISHED -> MaterialTheme.colorScheme.primary
  }

  Box {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = (row.depth * 20).dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (row.hasChildren) {
        IconButton(onClick = onExpandToggle) {
          Icon(
            if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = if (expanded) "子タスクを閉じる" else "子タスクを開く",
          )
        }
      } else {
        Spacer(Modifier.size(48.dp))
      }

      Checkbox(checked = task.completed, onCheckedChange = onCompletedChange)

      Column(
        Modifier
          .weight(1f)
          .semantics {
            onLongClick(label = "タスク操作") {
              menuOpen = true
              true
            }
          }
          .pointerInput(task.id) {
            detectTapGestures(onLongPress = { menuOpen = true })
          }
          .padding(horizontal = 4.dp, vertical = 6.dp),
      ) {
        Text(
          text = task.title,
          style = MaterialTheme.typography.bodyLarge,
          textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
        )
        if (task.description.isNotBlank()) {
          Text(
            text = task.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
          )
        }
        Text(
          text = taskStatusLabel(task, status),
          style = MaterialTheme.typography.labelMedium,
          color = statusColor,
        )
      }
    }

    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text("子タスクを追加") },
        leadingIcon = { Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null) },
        onClick = { menuOpen = false; onAddChild() },
      )
      DropdownMenuItem(
        text = { Text("編集") },
        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
        onClick = { menuOpen = false; onEdit() },
      )
      DropdownMenuItem(
        text = { Text("削除") },
        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
        onClick = { menuOpen = false; onDelete() },
      )
    }
  }
}

@Composable
private fun TaskEditorDialog(
  request: TaskEditorRequest,
  onDismiss: () -> Unit,
  onSave: (String, String, LocalDate?) -> Unit,
) {
  var title by remember(request.task?.id, request.parentId) { mutableStateOf(request.task?.title.orEmpty()) }
  var description by remember(request.task?.id, request.parentId) { mutableStateOf(request.task?.description.orEmpty()) }
  var dueDate by remember(request.task?.id, request.parentId) { mutableStateOf(request.task?.dueDate) }
  var showDatePicker by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (request.task == null) "タスクを追加" else "タスクを編集") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("タスク名") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("説明") },
          minLines = 3,
          maxLines = 6,
          modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        OutlinedTextField(
          value = dueDate?.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) ?: "期日なし",
          onValueChange = {},
          label = { Text("期日") },
          readOnly = true,
          modifier = Modifier.fillMaxWidth(),
          trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
              Icon(Icons.Default.CalendarMonth, contentDescription = "期日を選択")
            }
          },
        )
        if (dueDate != null) TextButton(onClick = { dueDate = null }) { Text("期日を解除") }
      }
    },
    confirmButton = {
      TextButton(
        enabled = title.isNotBlank(),
        onClick = { onSave(title.trim(), description.trim(), dueDate) },
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )

  if (showDatePicker) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate?.toUtcMillis())
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            dueDate = pickerState.selectedDateMillis?.toLocalDateUtc()
            showDatePicker = false
          },
        ) { Text("決定") }
      },
      dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } },
    ) { DatePicker(state = pickerState) }
  }
}

private fun taskStatusLabel(task: TaskItem, status: TaskStatus): String {
  val due = task.dueDate?.format(DateTimeFormatter.ofPattern("M/d"))
  return when (status) {
    TaskStatus.COMPLETED -> if (due == null) "完了" else "完了 · 期日 $due"
    TaskStatus.OVERDUE -> "期日超過 · $due"
    TaskStatus.UNFINISHED -> if (due == null) "未完了" else "未完了 · 期日 $due"
  }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
