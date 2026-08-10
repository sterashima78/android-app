package dev.terashima.yomitorirss.feature.article.data

import dev.terashima.yomitorirss.feature.article.ArticleRepository
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultArticleRepositoryContractTest {
  @Test
  fun `実装はArticleRepository契約を満たす`() {
    assertTrue(ArticleRepository::class.java.isAssignableFrom(DefaultArticleRepository::class.java))
  }
}
