package dev.terashima.yomitorirss.feature.asset

import org.junit.Assert.assertEquals
import org.junit.Test

class AssetCategoryGroupingTest {
  @Test
  fun `登録カテゴリごとに資産項目をまとめる`() {
    val groups = groupAssetCategorySettings(
      categories = listOf("その他", "現金", "投資"),
      settings = listOf(
        AssetCategorySetting("普通預金", "現金"),
        AssetCategorySetting("投資信託", "投資"),
        AssetCategorySetting("証券口座", "投資"),
      ),
    )

    assertEquals(listOf("その他", "現金", "投資"), groups.map { it.category })
    assertEquals(emptyList<AssetCategorySetting>(), groups[0].entries)
    assertEquals(listOf("普通預金"), groups[1].entries.map { it.assetName })
    assertEquals(listOf("投資信託", "証券口座"), groups[2].entries.map { it.assetName })
  }

  @Test
  fun `登録一覧にない既存カテゴリも失わず表示する`() {
    val groups = groupAssetCategorySettings(
      categories = listOf("その他"),
      settings = listOf(AssetCategorySetting("テスト資産", "既存カテゴリ")),
    )

    assertEquals(listOf("その他", "既存カテゴリ"), groups.map { it.category })
    assertEquals(listOf("テスト資産"), groups.last().entries.map { it.assetName })
  }
}
