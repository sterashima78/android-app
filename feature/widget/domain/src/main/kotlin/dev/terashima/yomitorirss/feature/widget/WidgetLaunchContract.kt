package dev.terashima.yomitorirss.feature.widget

/** Intent contract shared by widget UI components and executable app entry routing. */
object WidgetLaunchContract {
  const val ACTION_OPEN_TASKS = "dev.terashima.yomitorirss.widget.action.OPEN_TASKS"
  const val ACTION_OPEN_ARTICLE = "dev.terashima.yomitorirss.widget.action.OPEN_ARTICLE"
  const val EXTRA_ARTICLE_URL = "article_url"
}
