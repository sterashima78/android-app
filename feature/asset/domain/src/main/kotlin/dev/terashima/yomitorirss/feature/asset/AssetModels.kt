package dev.terashima.yomitorirss.feature.asset

import java.time.LocalDate

data class AssetHistoryPoint(
  val date: LocalDate,
  val total: Long,
  val byCategory: Map<String, Long>,
)

data class AssetCategorySetting(
  val assetName: String,
  val category: String,
)

data class AssetOverview(
  val latestDate: LocalDate?,
  val total: Long,
  val latestByCategory: Map<String, Long>,
  val history: List<AssetHistoryPoint>,
  val categorySettings: List<AssetCategorySetting>,
  val registeredCategories: List<String>,
)

data class AssetImportResult(
  val rowCount: Int,
  val snapshotCount: Int,
)
