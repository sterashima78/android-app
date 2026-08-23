package dev.terashima.yomitorirss.feature.widget

interface WidgetRefreshScheduler {
  fun enqueue()
}

/** Narrow framework boundary used by Android-created widget entry points. */
interface WidgetRefreshSchedulerProvider {
  val widgetRefreshScheduler: WidgetRefreshScheduler
}
