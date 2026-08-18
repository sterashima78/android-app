package dev.terashima.yomitorirss.feature.asset

interface AssetRepository {
  suspend fun loadOverview(): AssetOverview
  suspend fun importTsv(documentUri: String): AssetImportResult
  suspend fun importMoneyForwardJson(json: String): AssetImportResult
  suspend fun addCategory(category: String)
  suspend fun setCategory(assetName: String, category: String)
}
