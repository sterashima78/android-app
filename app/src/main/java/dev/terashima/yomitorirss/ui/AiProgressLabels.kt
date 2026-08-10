package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.settings.AiSummaryProgress

internal fun summaryProgressLabel(progress: AiSummaryProgress): String = when (progress.stage) {
  "preparing_model" -> "${progress.modelName ?: "モデル"} を準備しています"
  "generating_summary" -> "${progress.modelName ?: "モデル"} で要約を生成しています"
  else -> progress.modelName?.let { "${progress.stage}: $it" } ?: progress.stage
}
