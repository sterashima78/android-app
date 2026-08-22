package dev.terashima.yomitorirss.feature.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun NutritionSummaryCard(
  intakes: List<DailyNutritionIntake>,
  modifier: Modifier = Modifier,
) {
  val intake = intakes.maxByOrNull(DailyNutritionIntake::date)
  Card(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("栄養摂取", style = MaterialTheme.typography.titleMedium)
      if (intake == null) {
        Text(
          "選択日に栄養データはありません。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }
      Text(
        "${formatNutritionWhole(intake.energyKcal)} kcal",
        style = MaterialTheme.typography.headlineSmall,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("P ${formatNutritionDecimal(intake.proteinGrams)} g")
        Text("F ${formatNutritionDecimal(intake.fatGrams)} g")
        Text("C ${formatNutritionDecimal(intake.carbohydrateGrams)} g")
      }
      Text(
        "Health Connect に記録された当日分の合計です。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun formatNutritionWhole(value: Double): String =
  String.format(Locale.getDefault(), "%.0f", value)

private fun formatNutritionDecimal(value: Double): String =
  String.format(Locale.getDefault(), "%.1f", value)
