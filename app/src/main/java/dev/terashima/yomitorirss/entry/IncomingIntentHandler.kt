package dev.terashima.yomitorirss.entry

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.terashima.yomitorirss.MainActivityDependencies
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.widget.WidgetLaunchContract
import dev.terashima.yomitorirss.platform.openWebContentInCustomTab
import dev.terashima.yomitorirss.ui.AppNavigationTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class IncomingIntentHandler(
  private val activity: ComponentActivity,
  private val onNavigate: (AppNavigationTarget) -> Unit,
  private val dependencies: MainActivityDependencies,
) {
  fun consume(incoming: Intent) {
    consumeSharedLibrary(incoming)
    consumeSharedBookmark(incoming)
    consumeTaskWidget(incoming)
    consumeWidgetArticle(incoming)
  }

  private fun consumeTaskWidget(incoming: Intent) {
    val target = widgetLaunchTarget(incoming.action) ?: return
    incoming.action = null
    onNavigate(target)
  }

  private fun consumeWidgetArticle(incoming: Intent) {
    if (incoming.action != WidgetLaunchContract.ACTION_OPEN_ARTICLE) return
    val url = incoming.getStringExtra(WidgetLaunchContract.EXTRA_ARTICLE_URL)
      ?.trim()
      .orEmpty()
    incoming.action = null
    incoming.removeExtra(WidgetLaunchContract.EXTRA_ARTICLE_URL)
    if (url.isBlank()) return

    if (!activity.openWebContentInCustomTab(url)) {
      Toast.makeText(activity, "記事を開けませんでした", Toast.LENGTH_LONG).show()
    }
  }

  private fun consumeSharedLibrary(incoming: Intent) {
    if (incoming.action != ACTION_ADD_SHARED_URL_TO_LIBRARY) return

    val shared = parseSharedBookmark(
      text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT),
      subject = incoming.getCharSequenceExtra(Intent.EXTRA_SUBJECT),
    )
    clearSharePayload(incoming)

    if (shared == null) {
      Toast.makeText(activity, "共有内容に http/https の URL がありません", Toast.LENGTH_LONG).show()
      return
    }

    activity.lifecycleScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          dependencies.addSharedWebBook(shared.url, shared.title)
        }
      }.onSuccess { book ->
        onNavigate(AppNavigationTarget.LIBRARY)
        Toast.makeText(
          activity,
          "「${book.title}」を蔵書へ追加しました",
          Toast.LENGTH_SHORT,
        ).show()
      }.onFailure { error ->
        val message = error.message?.takeIf(String::isNotBlank) ?: "蔵書への追加に失敗しました"
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun consumeSharedBookmark(incoming: Intent) {
    if (incoming.action != Intent.ACTION_SEND || incoming.type != "text/plain") return

    val bookmark = parseSharedBookmark(
      text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT),
      subject = incoming.getCharSequenceExtra(Intent.EXTRA_SUBJECT),
    )
    clearSharePayload(incoming)

    if (bookmark == null) {
      Toast.makeText(activity, "共有内容にブックマークできるURLがありません", Toast.LENGTH_LONG).show()
      return
    }

    activity.lifecycleScope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          dependencies.saveSharedArticle(bookmark.url, bookmark.title, bookmark.sourceTitle)
        }
      }.onSuccess { result ->
        onNavigate(AppNavigationTarget.BOOKMARKS)
        val message = when (result) {
          BookmarkSaveResult.ADDED -> "ブックマークに追加しました"
          BookmarkSaveResult.ALREADY_BOOKMARKED -> "すでにブックマークされています"
        }
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
      }.onFailure { error ->
        val message = error.message?.takeIf(String::isNotBlank) ?: "ブックマークを保存できませんでした"
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun clearSharePayload(incoming: Intent) {
    incoming.action = null
    incoming.removeExtra(Intent.EXTRA_TEXT)
    incoming.removeExtra(Intent.EXTRA_SUBJECT)
  }

  companion object {
    const val ACTION_ADD_SHARED_URL_TO_LIBRARY =
      "dev.terashima.yomitorirss.action.ADD_SHARED_URL_TO_LIBRARY"
  }
}

internal fun widgetLaunchTarget(action: String?): AppNavigationTarget? =
  when (action) {
    WidgetLaunchContract.ACTION_OPEN_TASKS -> AppNavigationTarget.TASKS
    else -> null
  }
