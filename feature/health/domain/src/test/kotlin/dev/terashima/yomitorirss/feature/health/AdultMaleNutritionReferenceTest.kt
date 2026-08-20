package dev.terashima.yomitorirss.feature.health

import org.junit.Assert.assertEquals
import org.junit.Test

class AdultMaleNutritionReferenceTest {
  @Test
  fun `標準目安は2025年版の30から49歳男性普通活動量を基準にする`() {
    val standard = AdultMaleNutritionReference.standard

    assertEquals(2750.0, standard.energyKcal.min, 0.001)
    assertEquals(2750.0, standard.energyKcal.max, 0.001)
    assertEquals(89.375, standard.proteinGrams.min, 0.001)
    assertEquals(137.5, standard.proteinGrams.max, 0.001)
    assertEquals(61.111, standard.fatGrams.min, 0.001)
    assertEquals(91.667, standard.fatGrams.max, 0.001)
    assertEquals(343.75, standard.carbohydrateGrams.min, 0.001)
    assertEquals(446.875, standard.carbohydrateGrams.max, 0.001)
    assertEquals(65.0, standard.proteinRecommendedGrams, 0.001)
  }

  @Test
  fun `減量参考は標準から500kcal減らしたエネルギーで栄養比率を再計算する`() {
    val weightLoss = AdultMaleNutritionReference.weightLoss

    assertEquals(2250.0, weightLoss.energyKcal.min, 0.001)
    assertEquals(73.125, weightLoss.proteinGrams.min, 0.001)
    assertEquals(112.5, weightLoss.proteinGrams.max, 0.001)
    assertEquals(50.0, weightLoss.fatGrams.min, 0.001)
    assertEquals(75.0, weightLoss.fatGrams.max, 0.001)
    assertEquals(281.25, weightLoss.carbohydrateGrams.min, 0.001)
    assertEquals(365.625, weightLoss.carbohydrateGrams.max, 0.001)
  }
}
