package dev.terashima.yomitorirss.feature.web.data

import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanWebServerBoundaryTest {
  @Test
  fun `LAN WebはDomain Repositoryを受け取りDatabaseを受け取らない`() {
    val parameterTypes = LanWebServer::class.java.declaredConstructors
      .flatMap { it.parameterTypes.asIterable() }
      .toSet()

    assertTrue(ArticleRepository::class.java in parameterTypes)
    assertTrue(BookmarkRepository::class.java in parameterTypes)
    assertTrue(FeedRepository::class.java in parameterTypes)
    assertFalse(parameterTypes.any { it.name.contains("YomitoriDatabase") || it.name.contains("DatabaseConnection") })
  }
}
