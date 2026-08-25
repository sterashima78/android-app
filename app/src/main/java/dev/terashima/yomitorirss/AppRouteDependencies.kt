package dev.terashima.yomitorirss

import android.app.Application

class AppRouteDependencies internal constructor(
  application: Application,
  container: AppContainer,
) {
  private val content = AppContentRouteDependencies(container)
  private val supporting = AppSupportingRouteDependencies(application, container)

  val libraryTransfers get() = content.libraryTransfers
  val rssViewModelFactory get() = content.rssViewModelFactory
  val redditViewModelFactory get() = content.redditViewModelFactory
  val feedViewModelFactory get() = content.feedViewModelFactory
  val bookmarkViewModelFactory get() = content.bookmarkViewModelFactory
  val mailViewModelFactory get() = content.mailViewModelFactory
  val mailAuthorization get() = content.mailAuthorization
  val summaryViewModelFactory get() = content.summaryViewModelFactory
  val chatViewModelFactory get() = content.chatViewModelFactory
  val knowledgeViewModelFactory get() = content.knowledgeViewModelFactory
  val library get() = content.library
  val youtubeViewModelFactory get() = content.youtubeViewModelFactory

  val backupViewModelFactory get() = supporting.backupViewModelFactory
  val aiSettingsViewModelFactory get() = supporting.aiSettingsViewModelFactory
  val aiTaskQueueRepository get() = supporting.aiTaskQueueRepository
  val assetViewModelFactory get() = supporting.assetViewModelFactory
  val health get() = supporting.health
  val taskViewModelFactory get() = supporting.taskViewModelFactory
  val calendarViewModelFactory get() = supporting.calendarViewModelFactory
  val workoutViewModelFactory get() = supporting.workoutViewModelFactory
  val xViewerCssRepository get() = supporting.xViewerCssRepository

  fun backgroundFetchWifiOnly(): Boolean = supporting.backgroundFetchWifiOnly()

  fun setBackgroundFetchWifiOnly(wifiOnly: Boolean) {
    supporting.setBackgroundFetchWifiOnly(wifiOnly)
  }
}
