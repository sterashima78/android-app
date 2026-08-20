package dev.terashima.yomitorirss.feature.workout.data

import android.content.Context
import dev.terashima.yomitorirss.feature.workout.WorkoutDay
import dev.terashima.yomitorirss.feature.workout.WorkoutExercise
import dev.terashima.yomitorirss.feature.workout.WorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.WorkoutHistory
import dev.terashima.yomitorirss.feature.workout.WorkoutRepository
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import dev.terashima.yomitorirss.feature.workout.WorkoutSnapshot
import dev.terashima.yomitorirss.feature.workout.WorkoutUnit
import dev.terashima.yomitorirss.feature.workout.defaultWorkoutExercises
import dev.terashima.yomitorirss.feature.workout.inferWorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.newWorkoutSnapshot
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

class DefaultWorkoutRepository(context: Context) : WorkoutRepository {
  private val preferences = context.getSharedPreferences("workout", Context.MODE_PRIVATE)

  override suspend fun load(): WorkoutSnapshot {
    val date = LocalDate.now().toString()
    val raw = preferences.getString(KEY_STATE, null) ?: return newWorkoutSnapshot(date)
    return runCatching { decode(JSONObject(raw)) }.getOrElse { newWorkoutSnapshot(date) }
  }

  override suspend fun save(snapshot: WorkoutSnapshot) {
    preferences.edit().putString(KEY_STATE, encode(snapshot).toString()).apply()
  }

  private fun encode(snapshot: WorkoutSnapshot): JSONObject = JSONObject().apply {
    put("version", snapshot.version)
    put("exercises", JSONArray().apply { snapshot.exercises.forEach { put(encodeExercise(it)) } })
    put("today", encodeDay(snapshot.today))
    put("history", JSONArray().apply { snapshot.history.forEach { put(encodeHistory(it)) } })
    put("lastAmounts", encodeIntMap(snapshot.lastAmounts))
    put("lastStepCounts", encodeIntMap(snapshot.lastStepCounts))
  }

  private fun decode(json: JSONObject): WorkoutSnapshot {
    val exercises = json.optJSONArray("exercises")?.objects()?.map(::decodeExercise).orEmpty()
    return WorkoutSnapshot(
      version = json.optInt("version", 1),
      exercises = exercises.ifEmpty { defaultWorkoutExercises() },
      today = json.optJSONObject("today")?.let(::decodeDay) ?: WorkoutDay(LocalDate.now().toString()),
      history = json.optJSONArray("history")?.objects()?.map(::decodeHistory).orEmpty().take(50),
      lastAmounts = json.optJSONObject("lastAmounts").toIntMap(),
      lastStepCounts = json.optJSONObject("lastStepCounts").toIntMap(),
    )
  }

  private fun encodeExercise(value: WorkoutExercise) = JSONObject().apply {
    put("id", value.id)
    put("name", value.name)
    put("targetSets", value.targetSets)
    put("unit", value.unit.name)
    put("type", value.type.name)
  }

  private fun decodeExercise(json: JSONObject): WorkoutExercise {
    val name = json.optString("name")
    val unit = enumOrDefault(json.optString("unit"), WorkoutUnit.REPS)
    return WorkoutExercise(
      id = json.optString("id"),
      name = when (name) {
        "腹筋" -> "リバースクランチ"
        "スクワット" -> "ランジ"
        else -> name
      },
      targetSets = json.optInt("targetSets", 3).coerceAtLeast(1),
      unit = unit,
      type = enumOrNull<WorkoutExerciseType>(json.optString("type")) ?: inferWorkoutExerciseType(name, unit),
    )
  }

  private fun encodeSet(value: WorkoutSet) = JSONObject().apply {
    put("id", value.id)
    put("exerciseId", value.exerciseId)
    put("exerciseName", value.exerciseName)
    put("unit", value.unit.name)
    put("type", value.type.name)
    put("amount", value.amount)
    value.steps?.let { put("steps", it) }
    put("memo", value.memo)
    put("recordedAt", value.recordedAt)
    put("startedAt", value.startedAt ?: JSONObject.NULL)
    put("finishedAt", value.finishedAt ?: JSONObject.NULL)
  }

  private fun decodeSet(json: JSONObject): WorkoutSet {
    val unit = enumOrDefault(json.optString("unit"), WorkoutUnit.REPS)
    val name = json.optString("exerciseName", json.optString("name"))
    return WorkoutSet(
      id = json.optString("id"),
      exerciseId = json.optString("exerciseId"),
      exerciseName = name,
      unit = unit,
      type = enumOrNull<WorkoutExerciseType>(json.optString("type")) ?: inferWorkoutExerciseType(name, unit),
      amount = json.optInt("amount"),
      steps = if (json.has("steps") && !json.isNull("steps")) json.optInt("steps") else null,
      memo = json.optString("memo"),
      recordedAt = json.optString("recordedAt", json.optString("at")),
      startedAt = json.nullableString("startedAt"),
      finishedAt = json.nullableString("finishedAt"),
    )
  }

  private fun encodeDay(value: WorkoutDay) = JSONObject().apply {
    put("date", value.date)
    put("startedAt", value.startedAt ?: JSONObject.NULL)
    put("sets", JSONArray().apply { value.sets.forEach { put(encodeSet(it)) } })
  }

  private fun decodeDay(json: JSONObject) = WorkoutDay(
    date = json.optString("date", LocalDate.now().toString()),
    startedAt = json.nullableString("startedAt"),
    sets = json.optJSONArray("sets")?.objects()?.map(::decodeSet)
      ?: json.optJSONArray("logs")?.objects()?.map(::decodeSet).orEmpty(),
  )

  private fun encodeHistory(value: WorkoutHistory) = JSONObject().apply {
    put("id", value.id)
    put("date", value.date)
    put("startedAt", value.startedAt ?: JSONObject.NULL)
    put("finishedAt", value.finishedAt)
    put("sets", JSONArray().apply { value.sets.forEach { put(encodeSet(it)) } })
  }

  private fun decodeHistory(json: JSONObject) = WorkoutHistory(
    id = json.optString("id"),
    date = json.optString("date"),
    startedAt = json.nullableString("startedAt"),
    finishedAt = json.optString("finishedAt"),
    sets = json.optJSONArray("sets")?.objects()?.map(::decodeSet)
      ?: json.optJSONArray("logs")?.objects()?.map(::decodeSet).orEmpty(),
  )

  private fun encodeIntMap(values: Map<String, Int>) = JSONObject().apply {
    values.forEach { (key, value) -> put(key, value) }
  }

  private fun JSONObject?.toIntMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optInt(it) }
  }

  private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

  private fun JSONObject.nullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)

  private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }

  private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
    enumOrNull<T>(value) ?: default

  private companion object {
    const val KEY_STATE = "state_v1"
  }
}
