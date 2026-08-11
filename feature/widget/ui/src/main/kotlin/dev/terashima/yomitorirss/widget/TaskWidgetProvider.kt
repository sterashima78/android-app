package dev.terashima.yomitorirss.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.terashima.yomitorirss.feature.task.TaskFilter
import dev.terashima.yomitorirss.feature.task.TaskItem
import dev.terashima.yomitorirss.feature.task.TaskTreeRow
import dev.terashima.yomitorirss.feature.task.taskTreeRows
import dev.terashima.yomitorirss.feature.widget.ui.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TaskWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    appWidgetIds.forEach { appWidgetId ->
      appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.task_widget_list)
      updateTaskWidget(context, appWidgetManager, appWidgetId)
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    when (intent.action) {
      ACTION_REFRESH -> TaskWidgetUpdater.updateAll(context.applicationContext)
      ACTION_ITEM -> handleItemAction(context, intent)
    }
  }

  private fun handleItemAction(context: Context, intent: Intent) {
    when (intent.getStringExtra(EXTRA_ITEM_ACTION)) {
      ITEM_ACTION_OPEN -> openTasks(context)
      ITEM_ACTION_COMPLETE -> {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)?.takeIf(String::isNotBlank) ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
          try {
            runCatching { appContext.requireTaskRepository().setCompleted(taskId, true) }
            TaskWidgetUpdater.updateAll(appContext)
          } finally {
            pendingResult.finish()
          }
        }
      }
    }
  }

  companion object {
    const val ACTION_OPEN_TASKS = "dev.terashima.yomitorirss.widget.action.OPEN_TASKS"
    private const val ACTION_REFRESH = "dev.terashima.yomitorirss.widget.action.REFRESH_TASKS"
    private const val ACTION_ITEM = "dev.terashima.yomitorirss.widget.action.TASK_ITEM"
    private const val EXTRA_TASK_ID = "task_id"
    private const val EXTRA_ITEM_ACTION = "task_item_action"
    private const val ITEM_ACTION_OPEN = "open"
    private const val ITEM_ACTION_COMPLETE = "complete"

    internal fun itemOpenIntent(): Intent =
      Intent().putExtra(EXTRA_ITEM_ACTION, ITEM_ACTION_OPEN)

    internal fun itemCompleteIntent(taskId: String): Intent =
      Intent()
        .putExtra(EXTRA_ITEM_ACTION, ITEM_ACTION_COMPLETE)
        .putExtra(EXTRA_TASK_ID, taskId)
  }
}

object TaskWidgetUpdater {
  fun updateAll(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, TaskWidgetProvider::class.java)
    manager.getAppWidgetIds(provider).forEach { appWidgetId ->
      manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.task_widget_list)
      updateTaskWidget(context, manager, appWidgetId)
    }
  }
}

private fun updateTaskWidget(
  context: Context,
  manager: AppWidgetManager,
  appWidgetId: Int,
) {
  val views = RemoteViews(context.packageName, R.layout.widget_tasks)

  val serviceIntent = Intent(context, TaskWidgetService::class.java).apply {
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
  }
  views.setRemoteAdapter(R.id.task_widget_list, serviceIntent)
  views.setEmptyView(R.id.task_widget_list, R.id.task_widget_empty)

  val itemIntent = Intent(context, TaskWidgetProvider::class.java).apply {
    action = TaskWidgetProvider.ACTION_ITEM
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
  }
  val itemFlags = PendingIntent.FLAG_UPDATE_CURRENT or
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
  views.setPendingIntentTemplate(
    R.id.task_widget_list,
    PendingIntent.getBroadcast(context, appWidgetId, itemIntent, itemFlags),
  )

  val refreshIntent = Intent(context, TaskWidgetProvider::class.java).apply {
    action = TaskWidgetProvider.ACTION_REFRESH
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
  }
  views.setOnClickPendingIntent(
    R.id.task_widget_refresh,
    PendingIntent.getBroadcast(
      context,
      appWidgetId,
      refreshIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    ),
  )

  taskLaunchIntent(context)?.let { launchIntent ->
    views.setOnClickPendingIntent(
      R.id.task_widget_header,
      PendingIntent.getActivity(
        context,
        appWidgetId + HEADER_REQUEST_CODE_OFFSET,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      ),
    )
  }

  manager.updateAppWidget(appWidgetId, views)
}

class TaskWidgetService : RemoteViewsService() {
  override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
    TaskRemoteViewsFactory(applicationContext)
}

private class TaskRemoteViewsFactory(
  private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
  private var rows: List<TaskTreeRow> = emptyList()

  override fun onCreate() = reload()

  override fun onDataSetChanged() = reload()

  override fun onDestroy() {
    rows = emptyList()
  }

  override fun getCount(): Int = rows.size

  override fun getViewAt(position: Int): RemoteViews? {
    val row = rows.getOrNull(position) ?: return null
    val task = row.task
    return RemoteViews(context.packageName, R.layout.widget_task_item).apply {
      setTextViewText(R.id.task_widget_item_title, row.widgetTitle())
      val dueLabel = context.dueLabel(task)
      if (dueLabel == null) {
        setViewVisibility(R.id.task_widget_item_due, View.GONE)
      } else {
        setViewVisibility(R.id.task_widget_item_due, View.VISIBLE)
        setTextViewText(R.id.task_widget_item_due, dueLabel)
      }
      setOnClickFillInIntent(
        R.id.task_widget_item_open,
        TaskWidgetProvider.itemOpenIntent(),
      )
      setOnClickFillInIntent(
        R.id.task_widget_item_complete,
        TaskWidgetProvider.itemCompleteIntent(task.id),
      )
    }
  }

  override fun getLoadingView(): RemoteViews? = null

  override fun getViewTypeCount(): Int = 1

  override fun getItemId(position: Int): Long =
    rows.getOrNull(position)?.task?.id?.hashCode()?.toLong() ?: position.toLong()

  override fun hasStableIds(): Boolean = true

  private fun reload() {
    val tasks = runBlocking { context.requireTaskRepository().listTasks() }
    val expandedIds = tasks.mapTo(mutableSetOf()) { it.id }
    rows = taskTreeRows(
      tasks = tasks,
      filter = TaskFilter.UNFINISHED,
      expandedIds = expandedIds,
    )
  }
}

private fun TaskTreeRow.widgetTitle(): String =
  buildString {
    repeat(depth.coerceIn(0, MAX_INDENT_DEPTH)) { append("　") }
    append(task.title)
  }

private fun Context.dueLabel(task: TaskItem): String? {
  val dueDate = task.dueDate ?: return null
  val formatted = TASK_DATE_FORMATTER.format(dueDate)
  val today = LocalDate.now()
  return when {
    dueDate.isBefore(today) -> getString(R.string.task_widget_due_overdue, formatted)
    dueDate == today -> getString(R.string.task_widget_due_today)
    else -> getString(R.string.task_widget_due_date, formatted)
  }
}

private fun openTasks(context: Context) {
  taskLaunchIntent(context)?.let { context.startActivity(it) }
}

private fun taskLaunchIntent(context: Context): Intent? =
  context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
    action = TaskWidgetProvider.ACTION_OPEN_TASKS
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  }

private val TASK_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
private const val HEADER_REQUEST_CODE_OFFSET = 20_000
private const val MAX_INDENT_DEPTH = 4
