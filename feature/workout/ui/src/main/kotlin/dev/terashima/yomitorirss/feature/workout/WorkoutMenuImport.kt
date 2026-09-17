package dev.terashima.yomitorirss.feature.workout

import org.json.JSONArray
import org.json.JSONObject

data class WorkoutMenuImportExercise(
  val id: String?,
  val name: String,
  val unit: WorkoutUnit,
  val type: WorkoutExerciseType,
  val targetSets: Int,
  val targets: List<Int>,
)

data class WorkoutMenuImportDraft(
  val name: String,
  val exercises: List<WorkoutMenuImportExercise>,
)

fun parseWorkoutMenuImport(raw: String): WorkoutMenuImportDraft {
  val payload = raw.substringAfter('{', missingDelimiterValue = "")
    .let { if (it.isEmpty()) raw else "{$it" }
    .substringBeforeLast('}', missingDelimiterValue = "")
    .let { if (it.isEmpty()) raw else "$it}" }
    .trim()
  require(payload.isNotEmpty()) { "JSONが空です" }
  val root = JSONObject(payload)
  require(root.optInt("version", 1) == 1) { "未対応のメニューバージョンです" }
  val name = root.optString("name").trim().ifEmpty { "インポートメニュー" }
  val values = root.optJSONArray("exercises") ?: error("exercises が必要です")
  val exercises = (0 until values.length()).map { index ->
    parseImportExercise(values.getJSONObject(index))
  }
  require(exercises.isNotEmpty()) { "種目が1件以上必要です" }
  return WorkoutMenuImportDraft(name = name, exercises = exercises)
}

private fun parseImportExercise(json: JSONObject): WorkoutMenuImportExercise {
  val name = json.optString("name").trim()
  require(name.isNotEmpty()) { "種目名が必要です" }
  val unit = when (json.optString("unit").lowercase()) {
    "seconds", "second", "sec", "秒" -> WorkoutUnit.SECONDS
    "reps", "rep", "回", "" -> WorkoutUnit.REPS
    else -> error("$name の unit が不正です")
  }
  val type = json.optString("type").takeIf(String::isNotBlank)?.let { rawType ->
    WorkoutExerciseType.entries.firstOrNull { it.name.equals(rawType, ignoreCase = true) }
      ?: error("$name の type が不正です")
  } ?: inferWorkoutExerciseType(name, unit)
  val setsValue = json.opt("sets")
  val targets = when (setsValue) {
    is JSONArray -> (0 until setsValue.length()).map { setsValue.getInt(it) }
    else -> json.optJSONArray("targets")?.let { array ->
      (0 until array.length()).map { array.getInt(it) }
    }.orEmpty()
  }
  require(targets.all { it > 0 }) { "$name のセット目標値は1以上にしてください" }
  val targetSets = when {
    targets.isNotEmpty() -> targets.size
    setsValue is Number -> setsValue.toInt()
    else -> json.optInt("targetSets", 1)
  }
  require(targetSets > 0) { "$name のセット数は1以上にしてください" }
  return WorkoutMenuImportExercise(
    id = json.optString("id").trim().takeIf(String::isNotEmpty),
    name = name,
    unit = unit,
    type = type,
    targetSets = targetSets,
    targets = targets,
  )
}
