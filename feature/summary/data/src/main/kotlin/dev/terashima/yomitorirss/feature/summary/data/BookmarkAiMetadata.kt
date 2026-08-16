package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant
import java.util.UUID

internal fun normalizeGeneratedTags(candidates: Iterable<String>): List<String> = candidates
  .asSequence()
  .map(::normalizeGeneratedTag)
  .filter { it.length in 1..40 }
  .distinctBy { it.lowercase() }
  .take(MAX_AUTO_TAGS)
  .toList()

private fun normalizeGeneratedTag(candidate: String): String {
  var value = candidate.trim()
    .removePrefix("-")
    .removePrefix("•")
    .trim()
    .trim('"', '\'', '`')
    .replace(Regex("^\\d+[.)、:]\\s*"), "")
    .trim()

  val prefixes = listOf("tags:", "tag:", "タグ:", "tags：", "tag：", "タグ：")
  prefixes.firstOrNull { prefix -> value.startsWith(prefix, ignoreCase = true) }
    ?.let { prefix -> value = value.drop(prefix.length).trim() }
  return value
}

internal fun YomitoriDatabase.listExistingTagNamesForAiEnrichment(): List<String> =
  readableDatabase.rawQuery(
    """
      SELECT t.name
      FROM tags t
      LEFT JOIN article_tags x ON x.tag_id=t.id
      GROUP BY t.id,t.name,t.normalized_name
      ORDER BY COUNT(x.article_id) DESC,t.normalized_name
      LIMIT ?
    """.trimIndent(),
    arrayOf(MAX_EXISTING_TAGS_IN_PROMPT.toString()),
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) add(cursor.getString(0))
    }
  }

internal fun YomitoriDatabase.isBookmarkedForAiEnrichment(articleId: String): Boolean =
  readableDatabase.rawQuery(
    "SELECT 1 FROM articles WHERE id=? AND saved_at IS NOT NULL LIMIT 1",
    arrayOf(articleId),
  ).use { cursor -> cursor.moveToFirst() }

internal fun YomitoriDatabase.addAiGeneratedTags(articleId: String, names: List<String>): Boolean {
  if (names.isEmpty()) return false
  val db = writableDatabase
  db.beginTransaction()
  return try {
    val isStillBookmarked = db.rawQuery(
      "SELECT 1 FROM articles WHERE id=? AND saved_at IS NOT NULL LIMIT 1",
      arrayOf(articleId),
    ).use { cursor -> cursor.moveToFirst() }
    if (!isStillBookmarked) {
      db.setTransactionSuccessful()
      return false
    }

    var changed = false
    names.forEach { rawName ->
      val display = displayTagName(rawName)
      if (display.isBlank()) return@forEach
      val normalized = display.lowercase()
      val tagId = db.rawQuery(
        "SELECT id FROM tags WHERE normalized_name=? LIMIT 1",
        arrayOf(normalized),
      ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
      } ?: UUID.randomUUID().toString().also { id ->
        db.insertWithOnConflict(
          "tags",
          null,
          ContentValues().apply {
            put("id", id)
            put("name", display)
            put("normalized_name", normalized)
            put("created_at", Instant.now().toString())
          },
          SQLiteDatabase.CONFLICT_IGNORE,
        )
        db.rawQuery(
          "SELECT id FROM tags WHERE normalized_name=? LIMIT 1",
          arrayOf(normalized),
        ).use { cursor ->
          check(cursor.moveToFirst()) { "自動生成タグを保存できませんでした" }
          cursor.getString(0)
        }
      }

      val inserted = db.insertWithOnConflict(
        "article_tags",
        null,
        ContentValues().apply {
          put("article_id", articleId)
          put("tag_id", tagId)
        },
        SQLiteDatabase.CONFLICT_IGNORE,
      )
      if (inserted != -1L) changed = true
    }
    db.setTransactionSuccessful()
    changed
  } finally {
    db.endTransaction()
  }
}

private fun displayTagName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
internal const val MAX_AUTO_TAGS = 5
private const val MAX_EXISTING_TAGS_IN_PROMPT = 100
