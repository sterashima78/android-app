package dev.terashima.yomitorirss

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.terashima.yomitorirss.entry.forwardLibraryShareIntent

class LibraryShareActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    forwardLibraryShareIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    forwardLibraryShareIntent(intent)
  }
}
