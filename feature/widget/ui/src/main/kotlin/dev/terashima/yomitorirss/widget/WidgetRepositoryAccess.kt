package dev.terashima.yomitorirss.feature.widget
import android.content.Context

internal fun Context.requireWidgetRepository(): WidgetRepository =
  (applicationContext as? WidgetRepositoryProvider)?.widgetRepository
    ?: error("Application must implement WidgetRepositoryProvider")
