package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.composition.route.AppContentRouteDependencies
import dev.terashima.yomitorirss.composition.route.AppSupportingRouteDependencies

class AppRouteDependencies(
  application: Application,
  container: AppContainer,
) {
  private val content = AppContentRouteDependencies(container)
  private val supporting = AppSupportingRouteDependencies(application, container)

  val rssViewModelFactory get() = content.rssViewModelFactory
  val redditViewModelFactory get() = content.redditViewModelFactory
  val feedViewModelFactory get() = content.feedViewModelFactory
  val bookmarkViewModelFactory get() = content.bookmarkViewModelFactory
  val reprocessBookmarkEnrichment get() = content.reprocessBookmarkEnrichment
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
  val workout get() = supporting.workout
  val xViewerCssRepository get() = supporting.xViewerCssRepository

  fun backgroundFetchWifiOnly(): Boolean = supporting.backgroundFetchWifiOnly()

  fun setBackgroundFetchWifiOnly(wifiOnly: Boolean) {
    supporting.setBackgroundFetchWifiOnly(wifiOnly)
  }
}
