package dev.terashima.yomitorirss.feature.workout.data

import android.content.Context
import dev.terashima.yomitorirss.feature.workout.WorkoutDay
import dev.terashima.yomitorirss.feature.workout.WorkoutExercise
import dev.terashima.yomitorirss.feature.workout.WorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.WorkoutHistory
import dev.terashima.yomitorirss.feature.workout.WorkoutFormQuality
import dev.terashima.yomitorirss.feature.workout.WorkoutLoad
import dev.terashima.yomitorirss.feature.workout.WorkoutLoadKind
import dev.terashima.yomitorirss.feature.workout.WorkoutMenu
import dev.terashima.yomitorirss.feature.workout.WorkoutMenuItem
import dev.terashima.yomitorirss.feature.workout.WorkoutMenuSource
import dev.terashima.yomitorirss.feature.workout.WorkoutRepository
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import dev.terashima.yomitorirss.feature.workout.WorkoutSnapshot
import dev.terashima.yomitorirss.feature.workout.WorkoutUnit
import dev.terashima.yomitorirss.feature.workout.defaultWorkoutExercises
import dev.terashima.yomitorirss.feature.workout.defaultWorkoutMenu
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
    return try {
      decode(JSONObject(raw))
    } catch (error: UnsupportedWorkoutStateVersionException) {
      throw error
    } catch (error: Exception) {
      // Keep the original payload intact for recovery instead of silently replacing it.
      throw CorruptWorkoutStateException(error)
    }
  }

  override suspend fun save(snapshot: WorkoutSnapshot) {
    preferences.edit().putString(KEY_STATE, encode(snapshot).toString()).apply()
  }

  private fun encode(snapshot: WorkoutSnapshot): JSONObject = JSONObject().apply {
    put("version", CURRENT_VERSION)
    put("exercises", JSONArray().apply { snapshot.exercises.forEach { put(encodeExercise(it)) } })
    put("menus", JSONArray().apply { snapshot.menus.forEach { put(encodeMenu(it)) } })
    put("today", encodeDay(snapshot.today))
    put("history", JSONArray().apply { snapshot.history.forEach { put(encodeHistory(it)) } })
    put("lastAmounts", encodeIntMap(snapshot.lastAmounts))
    put("lastStepCounts", encodeIntMap(snapshot.lastStepCounts))
  }

  private fun decode(json: JSONObject): WorkoutSnapshot {
    val version = json.optInt("version", MISSING_VERSION)
    return when (version) {
      CURRENT_VERSION -> decodeCurrent(json)
      else -> throw UnsupportedWorkoutStateVersionException(version)
    }
  }

  private fun decodeCurrent(json: JSONObject): WorkoutSnapshot {
    require(json.optJSONArray("exercises") != null) { "Missing workout exercises" }
    require(json.optJSONArray("menus") != null) { "Missing workout menus" }
    require(json.optJSONObject("today") != null) { "Missing workout day" }
    require(json.optJSONArray("history") != null) { "Missing workout history" }
    val exercises = decodeExercises(json)
    val menus = json.optJSONArray("menus")?.objects()?.map(::decodeMenu).orEmpty()
      .filter { menu -> menu.items.any { item -> exercises.any { it.id == item.exerciseId } } }
      .ifEmpty { listOf(defaultWorkoutMenu(exercises)) }
    return decodeSnapshot(json = json, exercises = exercises, menus = menus)
  }

  private fun decodeExercises(json: JSONObject): List<WorkoutExercise> =
    json.optJSONArray("exercises")?.objects()?.map(::decodeExercise).orEmpty()
      .ifEmpty { defaultWorkoutExercises() }

  private fun decodeSnapshot(
    json: JSONObject,
    exercises: List<WorkoutExercise>,
    menus: List<WorkoutMenu>,
  ): WorkoutSnapshot = WorkoutSnapshot(
    version = CURRENT_VERSION,
    exercises = exercises,
    menus = menus,
    today = json.optJSONObject("today")?.let(::decodeDay) ?: WorkoutDay(LocalDate.now().toString()),
    history = json.optJSONArray("history")?.objects()?.map(::decodeHistory).orEmpty().take(50),
    lastAmounts = json.optJSONObject("lastAmounts").toIntMap(),
    lastStepCounts = json.optJSONObject("lastStepCounts").toIntMap(),
  )

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
      name = name,
      targetSets = json.optInt("targetSets", 3).coerceAtLeast(1),
      unit = unit,
      type = enumOrNull<WorkoutExerciseType>(json.optString("type")) ?: inferWorkoutExerciseType(name, unit),
    )
  }

  private fun encodeMenu(value: WorkoutMenu) = JSONObject().apply {
    put("id", value.id)
    put("name", value.name)
    put("source", value.source.name)
    put("items", JSONArray().apply { value.items.forEach { put(encodeMenuItem(it)) } })
  }

  private fun decodeMenu(json: JSONObject): WorkoutMenu = WorkoutMenu(
    id = json.optString("id"),
    name = json.optString("name", "メニュー"),
    source = enumOrDefault(json.optString("source"), WorkoutMenuSource.PRESET),
    items = json.optJSONArray("items")?.objects()?.mapNotNull(::decodeMenuItem).orEmpty(),
  )

  private fun encodeMenuItem(value: WorkoutMenuItem) = JSONObject().apply {
    put("exerciseId", value.exerciseId)
    put("targetSets", value.targetSets)
    if (value.targets.isNotEmpty()) {
      put("targets", JSONArray().apply { value.targets.forEach(::put) })
    }
  }

  private fun decodeMenuItem(json: JSONObject): WorkoutMenuItem? {
    val exerciseId = json.optString("exerciseId")
    if (exerciseId.isBlank()) return null
    val targetSets = json.optInt("targetSets", 1).coerceAtLeast(1)
    val targets = json.optJSONArray("targets")?.ints().orEmpty().filter { it > 0 }
    return runCatching {
      WorkoutMenuItem(
        exerciseId = exerciseId,
        targetSets = targetSets,
        targets = targets.takeIf { it.size == targetSets }.orEmpty(),
      )
    }.getOrNull()
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
    value.rpe?.let { put("rpe", it) }
    value.formQuality?.let { put("formQuality", it.name) }
    value.load?.let { load ->
      put("load", JSONObject().put("kind", load.kind.name).put("value", load.value))
    }
    value.restSeconds?.let { put("restSeconds", it) }
  }

  private fun decodeSet(json: JSONObject): WorkoutSet {
    val unit = enumOrDefault(json.optString("unit"), WorkoutUnit.REPS)
    val name = json.optString("exerciseName")
    return WorkoutSet(
      id = json.optString("id"),
      exerciseId = json.optString("exerciseId"),
      exerciseName = name,
      unit = unit,
      type = enumOrNull<WorkoutExerciseType>(json.optString("type")) ?: inferWorkoutExerciseType(name, unit),
      amount = json.optInt("amount"),
      steps = if (json.has("steps") && !json.isNull("steps")) json.optInt("steps") else null,
      memo = json.optString("memo"),
      recordedAt = json.optString("recordedAt"),
      startedAt = json.nullableString("startedAt"),
      finishedAt = json.nullableString("finishedAt"),
      rpe = json.optInt("rpe").takeIf { it in 1..10 },
      formQuality = enumOrNull<WorkoutFormQuality>(json.optString("formQuality")),
      load = json.optJSONObject("load")?.let { load ->
        val kind = enumOrNull<WorkoutLoadKind>(load.optString("kind"))
        kind?.let { runCatching { WorkoutLoad(it, load.optString("value")) }.getOrNull() }
      },
      restSeconds = if (json.has("restSeconds") && !json.isNull("restSeconds")) {
        json.optInt("restSeconds", -1).takeIf { it >= 0 }
      } else null,
    )
  }

  private fun encodeDay(value: WorkoutDay) = JSONObject().apply {
    put("date", value.date)
    put("startedAt", value.startedAt ?: JSONObject.NULL)
    value.menu?.let { put("menu", encodeMenu(it)) }
    put("sets", JSONArray().apply { value.sets.forEach { put(encodeSet(it)) } })
  }

  private fun decodeDay(json: JSONObject) = WorkoutDay(
    date = json.optString("date", LocalDate.now().toString()),
    startedAt = json.nullableString("startedAt"),
    menu = json.optJSONObject("menu")?.let(::decodeMenu),
    sets = json.optJSONArray("sets")?.objects()?.map(::decodeSet).orEmpty(),
  )

  private fun encodeHistory(value: WorkoutHistory) = JSONObject().apply {
    put("id", value.id)
    put("date", value.date)
    put("startedAt", value.startedAt ?: JSONObject.NULL)
    put("finishedAt", value.finishedAt)
    put("sets", JSONArray().apply { value.sets.forEach { put(encodeSet(it)) } })
    value.menu?.let { put("menu", encodeMenu(it)) }
  }

  private fun decodeHistory(json: JSONObject) = WorkoutHistory(
    id = json.optString("id"),
    date = json.optString("date"),
    startedAt = json.nullableString("startedAt"),
    finishedAt = json.optString("finishedAt"),
    sets = json.optJSONArray("sets")?.objects()?.map(::decodeSet).orEmpty(),
    menu = json.optJSONObject("menu")?.let(::decodeMenu),
  )

  private fun encodeIntMap(values: Map<String, Int>) = JSONObject().apply {
    values.forEach { (key, value) -> put(key, value) }
  }

  private fun JSONObject?.toIntMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optInt(it) }
  }

  private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

  private fun JSONArray.ints(): List<Int> = (0 until length()).mapNotNull { index ->
    optInt(index).takeIf { it > 0 }
  }

  private fun JSONObject.nullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)

  private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }

  private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
    enumOrNull<T>(value) ?: default

  private companion object {
    const val KEY_STATE = "state_v1"
    const val MISSING_VERSION = 0
    const val CURRENT_VERSION = 4
  }
}

internal class UnsupportedWorkoutStateVersionException(
  val version: Int,
) : IllegalStateException("Unsupported workout state version: $version")

internal class CorruptWorkoutStateException(cause: Throwable) :
  IllegalStateException("Corrupt workout state; original payload retained", cause)
