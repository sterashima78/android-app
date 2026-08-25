package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.core.background.CloudAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences

internal class SummaryQueueExecutionPreferences(context: Context) {
  private val localDelegate = LocalAiBackgroundExecutionPreferences(context)
  private val cloudDelegate = CloudAiBackgroundExecutionPreferences(context)

  var localPaused: Boolean
    get() = localDelegate.paused
    set(value) {
      localDelegate.paused = value
    }

  var cloudPaused: Boolean
    get() = cloudDelegate.paused
    set(value) {
      cloudDelegate.paused = value
    }

  var resumeLocalWhenCharging: Boolean
    get() = localDelegate.resumeWhenCharging
    set(value) {
      localDelegate.resumeWhenCharging = value
    }

  companion object {
    internal const val LOCAL_PREFERENCES_NAME = LocalAiBackgroundExecutionPreferences.PREFERENCES_NAME
    internal const val CLOUD_PREFERENCES_NAME = CloudAiBackgroundExecutionPreferences.PREFERENCES_NAME
  }
}
