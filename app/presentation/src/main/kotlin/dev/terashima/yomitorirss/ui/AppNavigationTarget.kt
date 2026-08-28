package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.library.LIBRARY_ROUTE
import dev.terashima.yomitorirss.feature.task.TASKS_ROUTE

/** Semantic app destinations requested by executable entry points. */
enum class AppNavigationTarget {
  BOOKMARKS,
  LIBRARY,
  TASKS,
}

internal fun AppNavigationTarget.appRoute(): String = when (this) {
  AppNavigationTarget.BOOKMARKS -> BOOKMARKS_ROUTE
  AppNavigationTarget.LIBRARY -> LIBRARY_ROUTE
  AppNavigationTarget.TASKS -> TASKS_ROUTE
}
