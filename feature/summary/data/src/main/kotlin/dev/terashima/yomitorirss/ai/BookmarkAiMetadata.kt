package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant
import java.util.UUID

internal const val AUTO_TAG_PROMPT = """
次の記事情報から、後で検索しやすい日本語のタグを1〜5個生成してください。
- 内容を具体的に表す短い名詞または名詞句にする
- 一般的すぎる「記事」「ニュース」「まとめ」は使わない
- 同義語を重複させない
- 本文にない情報を推測しない
- 出力はタグ名だけをカンマ区切りで返す
- 説明、番号、Markdown、前置きは付けない
"""

internal fun parseGeneratedTags(raw: String): List<String> = raw
  .replace("[", "")
  .replace("]", "")
  .split(',', '、', '\n', ';')
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
private const val MAX_AUTO_TAGS = 5
