package dev.terashima.yomitorirss.core.database

import android.database.sqlite.SQLiteDatabase

enum class DatabaseMigrationPhase {
  BEFORE_SCHEMA,
  AFTER_SCHEMA,
}

class DatabaseMigration(
  val targetVersion: Int,
  val phase: DatabaseMigrationPhase = DatabaseMigrationPhase.AFTER_SCHEMA,
  val migrate: (SQLiteDatabase) -> Unit,
) {
  init {
    require(targetVersion > 1) { "Migration target version must be greater than 1." }
  }
}

class DatabaseSchemaContribution(
  val owner: String,
  val createSchema: (SQLiteDatabase) -> Unit,
  val migrations: List<DatabaseMigration> = emptyList(),
) {
  init {
    require(owner.isNotBlank()) { "Database schema contribution owner must not be blank." }
  }
}

class DatabaseSchema(
  val version: Int,
  val contributions: List<DatabaseSchemaContribution>,
) {
  init {
    require(version >= 1) { "Database version must be at least 1." }
    val owners = contributions.map(DatabaseSchemaContribution::owner)
    require(owners.size == owners.distinct().size) { "Database schema contribution owners must be unique." }
    require(contributions.flatMap(DatabaseSchemaContribution::migrations).all { it.targetVersion <= version }) {
      "Database migrations must not target a version newer than the database schema."
    }
  }

  internal fun create(db: SQLiteDatabase) {
    contributions.forEach { it.createSchema(db) }
  }

  internal fun migrate(
    db: SQLiteDatabase,
    oldVersion: Int,
    newVersion: Int,
    phase: DatabaseMigrationPhase,
  ) {
    migrationsFor(oldVersion, newVersion, phase).forEach { it.migrate(db) }
  }

  internal fun migrationsFor(
    oldVersion: Int,
    newVersion: Int,
    phase: DatabaseMigrationPhase,
  ): List<DatabaseMigration> = contributions
    .flatMap(DatabaseSchemaContribution::migrations)
    .filter { migration ->
      migration.phase == phase && oldVersion < migration.targetVersion && migration.targetVersion <= newVersion
    }
    .sortedBy(DatabaseMigration::targetVersion)
}

interface DatabaseSchemaProvider {
  val databaseSchema: DatabaseSchema
}

fun SQLiteDatabase.addColumnIfMissing(
  table: String,
  column: String,
  definition: String,
) {
  val exists = rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    var found = false
    while (cursor.moveToNext()) {
      if (cursor.getString(nameIndex) == column) {
        found = true
        break
      }
    }
    found
  }
  if (!exists) execSQL("ALTER TABLE $table ADD COLUMN $definition")
}
