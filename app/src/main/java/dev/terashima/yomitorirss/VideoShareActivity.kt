package dev.terashima.yomitorirss

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.terashima.yomitorirss.entry.forwardVideoShareIntent

class VideoShareActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    forwardVideoShareIntent(intent)
  }
}
