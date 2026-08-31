package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.bookmark.BOOKMARKS_ROUTE
import dev.terashima.yomitorirss.feature.integrated.ui.INTEGRATED_ROUTE
import dev.terashima.yomitorirss.feature.library.LIBRARY_ROUTE
import dev.terashima.yomitorirss.feature.settings.SETTINGS_ROUTE
import dev.terashima.yomitorirss.feature.task.TASKS_ROUTE

/** Semantic app destinations requested by executable entry points. */
enum class AppNavigationTarget {
  INTEGRATED,
  BOOKMARKS,
  LIBRARY,
  TASKS,
  WEB_SERVER,
}

internal fun AppNavigationTarget.appRoute(): String = when (this) {
  AppNavigationTarget.INTEGRATED -> INTEGRATED_ROUTE
  AppNavigationTarget.BOOKMARKS -> BOOKMARKS_ROUTE
  AppNavigationTarget.LIBRARY -> LIBRARY_ROUTE
  AppNavigationTarget.TASKS -> TASKS_ROUTE
  AppNavigationTarget.WEB_SERVER -> SETTINGS_ROUTE
}
