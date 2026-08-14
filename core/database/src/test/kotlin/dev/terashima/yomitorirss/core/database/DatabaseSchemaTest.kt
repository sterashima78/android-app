package dev.terashima.yomitorirss.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DatabaseSchemaTest {
  @Test
  fun `migration plan preserves phase and version order`() {
    val schema = DatabaseSchema(
      version = 12,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "mail",
          createSchema = { _ -> },
          migrations = listOf(
            DatabaseMigration(targetVersion = 11) { _ -> },
            DatabaseMigration(targetVersion = 6) { _ -> },
          ),
        ),
        DatabaseSchemaContribution(
          owner = "rss",
          createSchema = { _ -> },
          migrations = listOf(
            DatabaseMigration(
              targetVersion = 10,
              phase = DatabaseMigrationPhase.BEFORE_SCHEMA,
            ) { _ -> },
          ),
        ),
        DatabaseSchemaContribution(
          owner = "summary",
          createSchema = { _ -> },
          migrations = listOf(DatabaseMigration(targetVersion = 12) { _ -> }),
        ),
      ),
    )

    assertEquals(
      listOf(10),
      schema.migrationsFor(5, 12, DatabaseMigrationPhase.BEFORE_SCHEMA).map { it.targetVersion },
    )
    assertEquals(
      listOf(6, 11, 12),
      schema.migrationsFor(5, 12, DatabaseMigrationPhase.AFTER_SCHEMA).map { it.targetVersion },
    )
    assertEquals(
      listOf(11, 12),
      schema.migrationsFor(10, 12, DatabaseMigrationPhase.AFTER_SCHEMA).map { it.targetVersion },
    )
  }

  @Test
  fun `schema rejects duplicate owners`() {
    assertThrows(IllegalArgumentException::class.java) {
      DatabaseSchema(
        version = 1,
        contributions = listOf(
          DatabaseSchemaContribution(owner = "rss", createSchema = { _ -> }),
          DatabaseSchemaContribution(owner = "rss", createSchema = { _ -> }),
        ),
      )
    }
  }

  @Test
  fun `schema rejects migrations newer than its version`() {
    assertThrows(IllegalArgumentException::class.java) {
      DatabaseSchema(
        version = 11,
        contributions = listOf(
          DatabaseSchemaContribution(
            owner = "summary",
            createSchema = { _ -> },
            migrations = listOf(DatabaseMigration(targetVersion = 12) { _ -> }),
          ),
        ),
      )
    }
  }
}
