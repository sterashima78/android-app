package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences

internal class SummaryQueueExecutionPreferences(context: Context) {
  private val delegate = LocalAiBackgroundExecutionPreferences(context)

  var paused: Boolean
    get() = delegate.paused
    set(value) {
      delegate.paused = value
    }

  var resumeWhenCharging: Boolean
    get() = delegate.resumeWhenCharging
    set(value) {
      delegate.resumeWhenCharging = value
    }
}
