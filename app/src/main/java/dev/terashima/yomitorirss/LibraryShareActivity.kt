package dev.terashima.yomitorirss

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class LibraryShareActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    forwardShareIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    forwardShareIntent(intent)
  }

  private fun forwardShareIntent(incoming: Intent) {
    if (incoming.action != Intent.ACTION_SEND || incoming.type != "text/plain") {
      Toast.makeText(this, "共有された URL を読み取れませんでした", Toast.LENGTH_LONG).show()
      finish()
      return
    }

    startActivity(
      Intent(this, MainActivity::class.java).apply {
        action = MainActivity.ACTION_ADD_SHARED_URL_TO_LIBRARY
        incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let { putExtra(Intent.EXTRA_TEXT, it) }
        incoming.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      },
    )
    finish()
  }
}
