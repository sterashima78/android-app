package dev.terashima.yomitorirss.composition.audio

import android.app.Application
import dev.terashima.yomitorirss.feature.audio.AudioPlaybackController
import dev.terashima.yomitorirss.feature.audio.data.DefaultAudioPlaybackController
import dev.terashima.yomitorirss.feature.summary.SummaryReader
import dev.terashima.yomitorirss.feature.summary.SummaryRequester

internal class AppAudioRuntimeDependencies(
  application: Application,
  summaryReader: SummaryReader,
  summaryRequester: SummaryRequester,
) {
  val playbackController: AudioPlaybackController = DefaultAudioPlaybackController(
    context = application,
    summaryReader = summaryReader,
    summaryRequester = summaryRequester,
  )
}
