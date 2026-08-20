package dev.terashima.yomitorirss.feature.health.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionAggregationTest {
  @Test
  fun `同日の栄養レコードを日次合算する`() {
    val date = LocalDate.of(2026, 8, 20)

    val result = aggregateNutritionByDay(
      listOf(
        NutritionSample(
          date = date,
          energyKcal = 500.0,
          proteinGrams = 20.0,
          fatGrams = 15.0,
          carbohydrateGrams = 70.0,
        ),
        NutritionSample(
          date = date,
          energyKcal = 700.0,
          proteinGrams = 30.0,
          fatGrams = 20.0,
          carbohydrateGrams = 90.0,
        ),
      ),
    )

    assertEquals(1, result.size)
    assertEquals(date, result.single().date)
    assertEquals(1200.0, result.single().energyKcal, 0.001)
    assertEquals(50.0, result.single().proteinGrams, 0.001)
    assertEquals(35.0, result.single().fatGrams, 0.001)
    assertEquals(160.0, result.single().carbohydrateGrams, 0.001)
  }

  @Test
  fun `日付順に並べて返す`() {
    val older = LocalDate.of(2026, 8, 19)
    val newer = LocalDate.of(2026, 8, 20)

    val result = aggregateNutritionByDay(
      listOf(
        NutritionSample(newer, 800.0, 30.0, 20.0, 100.0),
        NutritionSample(older, 700.0, 25.0, 18.0, 90.0),
      ),
    )

    assertEquals(listOf(older, newer), result.map { it.date })
  }
}
