package dev.terashima.yomitorirss.feature.widget

import android.content.Context
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.task.TaskRepositoryProvider

internal fun Context.requireTaskRepository(): TaskRepository =
  (applicationContext as? TaskRepositoryProvider)?.taskRepository
    ?: error("Application must implement TaskRepositoryProvider")
