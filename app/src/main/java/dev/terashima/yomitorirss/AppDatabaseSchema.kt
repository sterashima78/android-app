package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.feature.article.data.articleDatabaseSchema
import dev.terashima.yomitorirss.feature.asset.data.assetDatabaseSchema
import dev.terashima.yomitorirss.feature.bookmark.data.bookmarkDatabaseSchema
import dev.terashima.yomitorirss.feature.knowledge.data.knowledgeDatabaseSchema
import dev.terashima.yomitorirss.feature.library.data.libraryDatabaseSchema
import dev.terashima.yomitorirss.feature.mail.data.mailDatabaseSchema
import dev.terashima.yomitorirss.feature.rss.data.rssDatabaseSchema
import dev.terashima.yomitorirss.feature.summary.data.summaryDatabaseSchema

internal val appDatabaseSchema = DatabaseSchema(
  version = 20,
  contributions = listOf(
    rssDatabaseSchema,
    articleDatabaseSchema,
    bookmarkDatabaseSchema,
    summaryDatabaseSchema,
    mailDatabaseSchema,
    libraryDatabaseSchema,
    knowledgeDatabaseSchema,
    assetDatabaseSchema,
  ),
)
