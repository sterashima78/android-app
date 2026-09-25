package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection

class RssRecommendationBackupRestoreInitializer(
  private val database: DatabaseConnection,
) {
  fun initialize() {
    database.localTransaction {
      ensureRssRecommendationSchema(this)
      delete("rss_recommendation_tasks", null, null)
    }
  }
}
