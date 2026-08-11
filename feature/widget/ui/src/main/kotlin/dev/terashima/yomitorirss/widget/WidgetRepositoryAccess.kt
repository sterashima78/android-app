package dev.terashima.yomitorirss.feature.widget

import android.content.Context
import dev.terashima.yomitorirss.feature.task.TaskRepository

internal fun Context.requireWidgetRepository(): WidgetRepository =
  (applicationContext as? WidgetRepositoryProvider)?.widgetRepository
    ?: error("Application must implement WidgetRepositoryProvider")

interface TaskRepositoryProvider {
  val taskRepository: TaskRepository
}

internal fun Context.requireTaskRepository(): TaskRepository =
  (applicationContext as? TaskRepositoryProvider)?.taskRepository
    ?: error("Application must implement TaskRepositoryProvider")
