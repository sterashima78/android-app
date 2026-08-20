package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.database.DatabaseMigration
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val libraryDatabaseSchema = DatabaseSchemaContribution(
  owner = "library",
  createSchema = { db ->
    db.execSQL(
      """
        CREATE TABLE IF NOT EXISTS smb_library_servers(
          id TEXT PRIMARY KEY NOT NULL,
          name TEXT NOT NULL,
          host TEXT NOT NULL,
          port INTEGER NOT NULL,
          share_name TEXT NOT NULL,
          root_path TEXT NOT NULL,
          username TEXT NOT NULL,
          domain_name TEXT NOT NULL,
          updated_at INTEGER NOT NULL
        )
      """.trimIndent(),
    )
    db.execSQL(
      """
        CREATE TABLE IF NOT EXISTS smb_cover_prefetch_queue(
          source_id TEXT PRIMARY KEY NOT NULL,
          title TEXT NOT NULL,
          status TEXT NOT NULL,
          downloaded_bytes INTEGER NOT NULL DEFAULT 0,
          total_bytes INTEGER NOT NULL DEFAULT 0,
          message TEXT,
          updated_at INTEGER NOT NULL
        )
      """.trimIndent(),
    )
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS idx_smb_cover_prefetch_status ON smb_cover_prefetch_queue(status, updated_at)",
    )
    ensureLibraryOrganizationSchema(db)
  },
  migrations = listOf(
    DatabaseMigration(targetVersion = 18) { db ->
      db.delete(
        "library_organization_batch_items",
        "status IN (?, ?)",
        arrayOf("PENDING_REVIEW", "DEFERRED"),
      )
      db.execSQL(
        """
          UPDATE library_organization_batches
          SET status = 'COMPLETED'
          WHERE status IN ('RUNNING', 'PAUSED')
            AND NOT EXISTS (
              SELECT 1
              FROM library_organization_batch_items item
              WHERE item.batch_id = library_organization_batches.batch_id
                AND item.status IN ('QUEUED', 'PROCESSING')
            )
        """.trimIndent(),
      )
    },
  ),
)
