package dev.terashima.yomitorirss.composition.video

import android.app.Activity
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfileRepository
import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory
import dev.terashima.yomitorirss.feature.video.VideoPlaybackResolver
import dev.terashima.yomitorirss.feature.video.VideoRepository
import dev.terashima.yomitorirss.feature.video.data.AndroidWebVideoExtractorClient
import dev.terashima.yomitorirss.feature.video.data.DefaultVideoPlaybackResolver
import dev.terashima.yomitorirss.feature.video.data.DefaultVideoRepository

internal class AppVideoRuntimeDependencies(
  database: DatabaseConnection,
  httpClient: HttpClient,
  smbMediaFileAccess: SmbMediaFileAccess,
  smbConnectionProfileRepository: SmbConnectionProfileRepository,
  resumedActivityProvider: () -> Activity?,
) {
  val runtime: VideoRuntimeDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val extractorClient = AndroidWebVideoExtractorClient(resumedActivityProvider)
    val repository = DefaultVideoRepository(
      database = database,
      httpClient = httpClient,
      smbMediaFileAccess = smbMediaFileAccess,
      smbConnectionProfiles = smbConnectionProfileRepository,
      webExtractorClient = extractorClient,
    )
    val playbackResolver = DefaultVideoPlaybackResolver(
      smbMediaFileAccess = smbMediaFileAccess,
      smbSources = repository::smbSources,
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
