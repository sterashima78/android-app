package dev.terashima.yomitorirss.feature.widget
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.terashima.yomitorirss.feature.widget.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class UnreadArticlesWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    appWidgetIds.forEach { appWidgetId -> updateWidget(context, appWidgetManager, appWidgetId) }
    if (appWidgetIds.isNotEmpty()) context.widgetRefreshScheduler().enqueue()
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    when (intent.action) {
      ACTION_REFRESH -> context.widgetRefreshScheduler().enqueue()
      ACTION_ITEM -> handleItemAction(context, intent)
    }
  }

  private fun handleItemAction(context: Context, intent: Intent) {
    when (intent.getStringExtra(EXTRA_ITEM_ACTION)) {
      ITEM_ACTION_OPEN -> {
        val url = intent.getStringExtra(EXTRA_ARTICLE_URL)?.trim().orEmpty()
        if (url.isBlank()) return
        runCatching {
          context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
          )
        }
      }

      ITEM_ACTION_MARK_READ,
      ITEM_ACTION_READ_LATER,
      -> {
        val articleId = intent.getStringExtra(EXTRA_ARTICLE_ID)?.takeIf(String::isNotBlank) ?: return
        val action = intent.getStringExtra(EXTRA_ITEM_ACTION) ?: return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
          try {
            runCatching {
              when (action) {
                ITEM_ACTION_MARK_READ -> appContext.widgetRepository().markRead(articleId)
                ITEM_ACTION_READ_LATER -> appContext.widgetRepository().markReadLater(articleId)
              }
            }
            UnreadArticlesWidgetUpdater.updateAll(appContext)
          } finally {
            pendingResult.finish()
          }
        }
      }
    }
  }

  companion object {
    const val ACTION_REFRESH = "dev.terashima.yomitorirss.widget.action.REFRESH_UNREAD"
    const val ACTION_OPEN_ARTICLE = WidgetLaunchContract.ACTION_OPEN_ARTICLE
    const val ACTION_ITEM = "dev.terashima.yomitorirss.widget.action.ITEM"
    const val EXTRA_ARTICLE_URL = WidgetLaunchContract.EXTRA_ARTICLE_URL
    const val EXTRA_ARTICLE_ID = "article_id"
    const val EXTRA_ITEM_ACTION = "item_action"
    const val ITEM_ACTION_OPEN = "open"
    const val ITEM_ACTION_MARK_READ = "mark_read"
    const val ITEM_ACTION_READ_LATER = "read_later"
  }
}

object UnreadArticlesWidgetUpdater {
  fun updateAll(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, UnreadArticlesWidgetProvider::class.java)
    manager.getAppWidgetIds(provider).forEach { appWidgetId ->
      manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.unread_widget_list)
      updateWidget(context, manager, appWidgetId)
    }
  }
}

private fun updateWidget(
  context: Context,
  manager: AppWidgetManager,
  appWidgetId: Int,
) {
  val unreadCount = context.widgetRepository().listUnreadArticles().size
  val views = RemoteViews(context.packageName, R.layout.widget_unread_articles)

  views.setTextViewText(
    R.id.unread_widget_count,
    context.getString(R.string.unread_widget_count, unreadCount),
  )

  val serviceIntent = Intent(context, UnreadArticlesWidgetService::class.java).apply {
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
  }
  views.setRemoteAdapter(R.id.unread_widget_list, serviceIntent)
  views.setEmptyView(R.id.unread_widget_list, R.id.unread_widget_empty)

  val itemIntent = Intent(context, UnreadArticlesWidgetProvider::class.java).apply {
    action = UnreadArticlesWidgetProvider.ACTION_ITEM
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
  }
  val itemFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
  views.setPendingIntentTemplate(
    R.id.unread_widget_list,
    PendingIntent.getBroadcast(context, appWidgetId, itemIntent, itemFlags),
  )

  val refreshIntent = Intent(context, UnreadArticlesWidgetProvider::class.java).apply {
    action = UnreadArticlesWidgetProvider.ACTION_REFRESH
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
  }
  views.setOnClickPendingIntent(
    R.id.unread_widget_refresh,
    PendingIntent.getBroadcast(
      context,
      appWidgetId,
      refreshIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    ),
  )

  context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
    views.setOnClickPendingIntent(
      R.id.unread_widget_header,
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

class UnreadArticlesWidgetService : RemoteViewsService() {
  override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
    UnreadArticlesRemoteViewsFactory(applicationContext)
}

private class UnreadArticlesRemoteViewsFactory(
  private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
  private var articles: List<WidgetArticle> = emptyList()

  override fun onCreate() = reload()

  override fun onDataSetChanged() = reload()

  override fun onDestroy() {
    articles = emptyList()
  }

  override fun getCount(): Int = articles.size

  override fun getViewAt(position: Int): RemoteViews? {
    val article = articles.getOrNull(position) ?: return null
    return RemoteViews(context.packageName, R.layout.widget_unread_article_item).apply {
      setTextViewText(R.id.unread_widget_item_title, article.title)
      setTextViewText(R.id.unread_widget_item_meta, article.widgetMeta())
      setOnClickFillInIntent(
        R.id.unread_widget_item,
        Intent()
          .putExtra(UnreadArticlesWidgetProvider.EXTRA_ITEM_ACTION, UnreadArticlesWidgetProvider.ITEM_ACTION_OPEN)
          .putExtra(UnreadArticlesWidgetProvider.EXTRA_ARTICLE_URL, article.url),
      )
      setOnClickFillInIntent(
        R.id.unread_widget_item_mark_read,
        Intent()
          .putExtra(UnreadArticlesWidgetProvider.EXTRA_ITEM_ACTION, UnreadArticlesWidgetProvider.ITEM_ACTION_MARK_READ)
          .putExtra(UnreadArticlesWidgetProvider.EXTRA_ARTICLE_ID, article.id),
      )
      setOnClickFillInIntent(
        R.id.unread_widget_item_read_later,
        Intent()
          .putExtra(UnreadArticlesWidgetProvider.EXTRA_ITEM_ACTION, UnreadArticlesWidgetProvider.ITEM_ACTION_READ_LATER)
          .putExtra(UnreadArticlesWidgetProvider.EXTRA_ARTICLE_ID, article.id),
      )
    }
  }

  override fun getLoadingView(): RemoteViews? = null

  override fun getViewTypeCount(): Int = 1

  override fun getItemId(position: Int): Long =
    articles.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

  override fun hasStableIds(): Boolean = true

  private fun reload() {
    articles = context.widgetRepository().listUnreadArticles()
  }
}

private fun Context.widgetRepository(): WidgetRepository =
  (applicationContext as? WidgetRepositoryProvider)?.widgetRepository
    ?: error("Application must implement WidgetRepositoryProvider")

private fun Context.widgetRefreshScheduler(): WidgetRefreshScheduler =
  (applicationContext as? WidgetRefreshSchedulerProvider)?.widgetRefreshScheduler
    ?: error("Application must implement WidgetRefreshSchedulerProvider")

private fun WidgetArticle.widgetMeta(): String {
  val published = runCatching {
    WIDGET_DATE_FORMATTER.format(Instant.parse(publishedAt))
  }.getOrNull()
  return listOfNotNull(sourceTitle.takeIf(String::isNotBlank), published).joinToString(" · ")
}

private val WIDGET_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("M/d H:mm").withZone(ZoneId.systemDefault())

private const val HEADER_REQUEST_CODE_OFFSET = 10_000
