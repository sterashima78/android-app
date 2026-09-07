package dev.terashima.yomitorirss.composition.video

import android.app.Activity
import android.app.Application
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.library.data.DefaultSmbMediaFileAccess
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoPlaybackResolver
import dev.terashima.yomitorirss.feature.video.VideoRepository
import dev.terashima.yomitorirss.feature.video.data.AndroidWebVideoExtractorClient
import dev.terashima.yomitorirss.feature.video.data.DefaultVideoPlaybackResolver
import dev.terashima.yomitorirss.feature.video.data.DefaultVideoRepository

internal class AppVideoRuntimeDependencies(
  application: Application,
  database: DatabaseConnection,
  httpClient: HttpClient,
  resumedActivityProvider: () -> Activity?,
) {
  val runtime: VideoRuntimeDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val smbMediaAccess = DefaultSmbMediaFileAccess(application, database)
    val extractorClient = AndroidWebVideoExtractorClient(resumedActivityProvider)
    val repository = DefaultVideoRepository(
      database = database,
      httpClient = httpClient,
      smbMediaFileAccess = smbMediaAccess,
      webExtractorClient = extractorClient,
    )
    val playbackResolver = DefaultVideoPlaybackResolver(
      smbMediaFileAccess = smbMediaAccess,
      rules = repository::extractorRules,
      webExtractorClient = extractorClient,
    )
    VideoRuntimeDependencies(
      repository = repository,
      playbackResolver = playbackResolver,
      byteSourceFactory = playbackResolver,
    )
  }
}

internal data class VideoRuntimeDependencies(
  val repository: VideoRepository,
  val playbackResolver: VideoPlaybackResolver,
  val byteSourceFactory: VideoByteSourceFactory,
)
