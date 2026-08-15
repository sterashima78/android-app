package dev.terashima.yomitorirss.feature.summary

fun summaryProgressLabel(stage: String, modelName: String?): String = when (stage) {
  "preparing_model" -> "${modelName ?: "モデル"} を準備しています"
  "generating_summary" -> "${modelName ?: "モデル"} で要約を生成しています"
  else -> modelName?.let { "$stage: $it" } ?: stage
}
