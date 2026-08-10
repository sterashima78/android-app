package dev.terashima.yomitorirss.feature.widget
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetArticleTest {
  @Test
  fun `ウィジェット記事は表示に必要な情報を保持する`() {
    val article = WidgetArticle(
      id = "article-1",
      url = "https://example.com/article",
      title = "Article",
      sourceTitle = "Source",
      publishedAt = "2026-08-08T00:00:00Z",
    )

    assertEquals("article-1", article.id)
    assertEquals("Article", article.title)
    assertEquals("Source", article.sourceTitle)
  }
}
