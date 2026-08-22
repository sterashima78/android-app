package dev.terashima.yomitorirss

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.terashima.yomitorirss.feature.library.WebLibraryMutatorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryShareActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    consumeShareIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumeShareIntent(intent)
  }

  private fun consumeShareIntent(intent: Intent) {
    if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") {
      finishWithMessage("共有された URL を読み取れませんでした")
      return
    }
    val shared = parseSharedBookmark(
      text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
      subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT),
    )
    if (shared == null) {
      finishWithMessage("共有内容に http/https の URL がありません")
      return
    }
    val provider = applicationContext as? WebLibraryMutatorProvider
    if (provider == null) {
      finishWithMessage("蔵書への追加を開始できませんでした")
      return
    }

    lifecycleScope.launch {
      val result = runCatching {
        withContext(Dispatchers.IO) {
          provider.webLibraryMutator.addWebBook(shared.url, shared.title)
        }
      }
      result
        .onSuccess { book -> finishWithMessage("「${book.title}」を蔵書へ追加しました") }
        .onFailure { error -> finishWithMessage(error.message ?: "蔵書への追加に失敗しました") }
    }
  }

  private fun finishWithMessage(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    finish()
  }
}
