package dev.terashima.yomitorirss.composition.route

import dev.terashima.yomitorirss.AppContainer
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoPlaybackResolver
import dev.terashima.yomitorirss.feature.video.ui.VideoViewModel

internal class AppVideoRouteDependencies(
  private val container: AppContainer,
) {
  val dependencies: VideoRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val runtime = container.videoRuntime
    VideoRouteDependencies(
      viewModelFactory = VideoViewModel.Factory(
        repository = runtime.repository,
        providerRepository = runtime.providerRepository,
        smbConnectionProfiles = container.smbConnectionProfileRepository,
      ),
      playbackResolver = runtime.playbackResolver,
      byteSourceFactory = runtime.byteSourceFactory,
    )
  }
}

data class VideoRouteDependencies internal constructor(
  val viewModelFactory: VideoViewModel.Factory,
  val playbackResolver: VideoPlaybackResolver,
  val byteSourceFactory: VideoByteSourceFactory,
)
