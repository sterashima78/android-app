package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryBookKey
import dev.terashima.yomitorirss.feature.library.LibraryCollection
import dev.terashima.yomitorirss.feature.library.LibraryItemOrganization
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationDraft
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationTag
import dev.terashima.yomitorirss.feature.library.LibraryReadingStatus
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.organizationKey
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultLibraryOrganizationRepository(
  private val database: DatabaseConnection,
) : LibraryOrganizationRepository {
  override suspend fun snapshot(): LibraryOrganizationSnapshot = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    val tags = queryTags()
    val collections = queryCollections()
    val tagsById = tags.associateBy(LibraryOrganizationTag::id)
    val collectionsById = collections.associateBy(LibraryCollection::id)
    val itemTags = queryItemTags(tagsById)
    val itemCollections = queryItemCollections(collectionsById)
    val readingStatuses = queryReadingStatuses()
    val keys = linkedSetOf<LibraryBookKey>().apply {
      addAll(itemTags.keys)
      addAll(itemCollections.keys)
      addAll(readingStatuses.keys)
    }
    LibraryOrganizationSnapshot(
      tags = tags,
      collections = collections,
      items = keys.associateWith { key ->
        LibraryItemOrganization(
          key = key,
          tags = itemTags[key].orEmpty(),
          collections = itemCollections[key].orEmpty(),
          readingStatus = readingStatuses[key],
        )
      },
    )
  }

  override suspend fun save(
    book: LibraryBook,
    draft: LibraryOrganizationDraft,
  ): Unit = withContext(Dispatchers.IO) {
    val tagNames = sanitizeNames(draft.tagNames, MAX_TAGS_PER_BOOK, "タグ")
    val collectionNames = sanitizeNames(draft.collectionNames, MAX_COLLECTIONS_PER_BOOK, "コレクション")
    ensureLibraryOrganizationSchema(database.writable)
    val key = book.organizationKey()
    val now = System.currentTimeMillis()
    database.transaction {
      delete(
        ITEM_TAG_TABLE,
        "source = ? AND source_id = ?",
        arrayOf(key.source.name, key.sourceId),
      )
      tagNames.forEach { name ->
        val tagId = resolveTaxonomyId(
          table = TAG_TABLE,
          idColumn = "tag_id",
          name = name,
          idPrefix = "ltag",
          now = now,
        )
        insertOrThrow(
          ITEM_TAG_TABLE,
          null,
          ContentValues().apply {
            put("source", key.source.name)
            put("source_id", key.sourceId)
            put("tag_id", tagId)
            put("created_at", now)
          },
        )
      }

      delete(
        ITEM_COLLECTION_TABLE,
        "source = ? AND source_id = ?",
        arrayOf(key.source.name, key.sourceId),
      )
      collectionNames.forEach { name ->
        val collectionId = resolveTaxonomyId(
          table = COLLECTION_TABLE,
          idColumn = "collection_id",
          name = name,
          idPrefix = "lcol",
          now = now,
        )
        insertOrThrow(
          ITEM_COLLECTION_TABLE,
          null,
          ContentValues().apply {
            put("source", key.source.name)
            put("source_id", key.sourceId)
            put("collection_id", collectionId)
            put("created_at", now)
          },
        )
      }

      if (draft.readingStatus == null) {
        delete(
          READING_STATUS_TABLE,
          "source = ? AND source_id = ?",
          arrayOf(key.source.name, key.sourceId),
        )
      } else {
        insertWithOnConflict(
          READING_STATUS_TABLE,
          null,
          ContentValues().apply {
            put("source", key.source.name)
            put("source_id", key.sourceId)
            put("status", draft.readingStatus.name)
            put("updated_at", now)
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
      }
      Unit
    }
  }

  private fun queryTags(): List<LibraryOrganizationTag> = database.readable.rawQuery(
    "SELECT tag_id, name, normalized_name FROM $TAG_TABLE ORDER BY name COLLATE NOCASE",
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          LibraryOrganizationTag(
            id = cursor.getString(0),
            name = cursor.getString(1),
            normalizedName = cursor.getString(2),
          ),
        )
      }
    }
  }

  private fun queryCollections(): List<LibraryCollection> = database.readable.rawQuery(
    "SELECT collection_id, name, normalized_name FROM $COLLECTION_TABLE ORDER BY name COLLATE NOCASE",
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          LibraryCollection(
            id = cursor.getString(0),
            name = cursor.getString(1),
            normalizedName = cursor.getString(2),
          ),
        )
      }
    }
  }

  private fun queryItemTags(
    tagsById: Map<String, LibraryOrganizationTag>,
  ): Map<LibraryBookKey, List<LibraryOrganizationTag>> = database.readable.rawQuery(
    "SELECT source, source_id, tag_id FROM $ITEM_TAG_TABLE ORDER BY created_at, tag_id",
    null,
  ).use { cursor ->
    buildMap<LibraryBookKey, MutableList<LibraryOrganizationTag>> {
      while (cursor.moveToNext()) {
        val key = LibraryBookKey(LibrarySource.valueOf(cursor.getString(0)), cursor.getString(1))
        tagsById[cursor.getString(2)]?.let { tag -> getOrPut(key) { mutableListOf() }.add(tag) }
      }
    }
  }

  private fun queryItemCollections(
    collectionsById: Map<String, LibraryCollection>,
  ): Map<LibraryBookKey, List<LibraryCollection>> = database.readable.rawQuery(
    "SELECT source, source_id, collection_id FROM $ITEM_COLLECTION_TABLE ORDER BY created_at, collection_id",
    null,
  ).use { cursor ->
    buildMap<LibraryBookKey, MutableList<LibraryCollection>> {
      while (cursor.moveToNext()) {
        val key = LibraryBookKey(LibrarySource.valueOf(cursor.getString(0)), cursor.getString(1))
        collectionsById[cursor.getString(2)]?.let { collection ->
          getOrPut(key) { mutableListOf() }.add(collection)
        }
      }
    }
  }

  private fun queryReadingStatuses(): Map<LibraryBookKey, LibraryReadingStatus> =
    database.readable.rawQuery(
      "SELECT source, source_id, status FROM $READING_STATUS_TABLE",
      null,
    ).use { cursor ->
      buildMap {
        while (cursor.moveToNext()) {
          val status = runCatching { LibraryReadingStatus.valueOf(cursor.getString(2)) }.getOrNull()
            ?: continue
          put(
            LibraryBookKey(LibrarySource.valueOf(cursor.getString(0)), cursor.getString(1)),
            status,
          )
        }
      }
    }

  private fun SQLiteDatabase.resolveTaxonomyId(
    table: String,
    idColumn: String,
    name: String,
    idPrefix: String,
    now: Long,
  ): String {
    val normalized = normalizeLibraryOrganizationName(name)
    rawQuery(
      "SELECT $idColumn FROM $table WHERE normalized_name = ?",
      arrayOf(normalized),
    ).use { cursor ->
      if (cursor.moveToFirst()) return cursor.getString(0)
    }
    val id = "$idPrefix-${UUID.randomUUID()}"
    insertOrThrow(
      table,
      null,
      ContentValues().apply {
        put(idColumn, id)
        put("name", name)
        put("normalized_name", normalized)
        put("created_at", now)
      },
    )
    return id
  }
}

internal fun ensureLibraryOrganizationSchema(db: SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $TAG_TABLE(
        tag_id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        normalized_name TEXT NOT NULL UNIQUE,
        created_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $COLLECTION_TABLE(
        collection_id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        normalized_name TEXT NOT NULL UNIQUE,
        created_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $ITEM_TAG_TABLE(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        tag_id TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id, tag_id),
        FOREIGN KEY(tag_id) REFERENCES $TAG_TABLE(tag_id) ON DELETE CASCADE
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $ITEM_COLLECTION_TABLE(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        collection_id TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id, collection_id),
        FOREIGN KEY(collection_id) REFERENCES $COLLECTION_TABLE(collection_id) ON DELETE CASCADE
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $READING_STATUS_TABLE(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        status TEXT NOT NULL,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id)
      )
    """.trimIndent(),
  )
  db.execSQL("CREATE INDEX IF NOT EXISTS library_item_org_tags_tag ON $ITEM_TAG_TABLE(tag_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS library_item_org_collections_collection ON $ITEM_COLLECTION_TABLE(collection_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS library_item_reading_status_status ON $READING_STATUS_TABLE(status)")
}

internal fun normalizeLibraryOrganizationName(value: String): String =
  cleanLibraryOrganizationName(value).lowercase(Locale.ROOT)

private fun cleanLibraryOrganizationName(value: String): String =
  value.trim().replace(Regex("\\s+"), " ")

private fun sanitizeNames(
  values: List<String>,
  maxCount: Int,
  label: String,
): List<String> {
  val sanitized = values
    .map(::cleanLibraryOrganizationName)
    .filter(String::isNotEmpty)
    .distinctBy(::normalizeLibraryOrganizationName)
  require(sanitized.size <= maxCount) { "$label は最大 $maxCount 件まで設定できます" }
  require(sanitized.all { it.length <= MAX_NAME_LENGTH }) { "$label 名は $MAX_NAME_LENGTH 文字以内で入力してください" }
  return sanitized
}

private const val TAG_TABLE = "library_organization_tags"
private const val COLLECTION_TABLE = "library_organization_collections"
private const val ITEM_TAG_TABLE = "library_item_organization_tags"
private const val ITEM_COLLECTION_TABLE = "library_item_organization_collections"
private const val READING_STATUS_TABLE = "library_item_reading_status"
private const val MAX_TAGS_PER_BOOK = 20
private const val MAX_COLLECTIONS_PER_BOOK = 10
private const val MAX_NAME_LENGTH = 80
