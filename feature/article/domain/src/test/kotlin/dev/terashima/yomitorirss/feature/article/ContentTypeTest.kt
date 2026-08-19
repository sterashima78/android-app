package dev.terashima.yomitorirss.feature.article

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentTypeTest {
  @Test
  fun `記事指定はフィードとフォルダより優先する`() {
    assertEquals(
      ContentType.ARTICLE,
      resolveContentType(
        articleOverride = ContentType.ARTICLE,
        feedOverride = ContentType.COMIC,
        folderOverride = ContentType.COMIC,
      ),
    )
  }

  @Test
  fun `記事未指定ならフィード指定を優先する`() {
    assertEquals(
      ContentType.COMIC,
      resolveContentType(
        articleOverride = null,
        feedOverride = ContentType.COMIC,
        folderOverride = ContentType.ARTICLE,
      ),
    )
  }

  @Test
  fun `記事とフィード未指定ならフォルダ指定を使う`() {
    assertEquals(
      ContentType.COMIC,
      resolveContentType(
        articleOverride = null,
        feedOverride = null,
        folderOverride = ContentType.COMIC,
      ),
    )
  }

  @Test
  fun `すべて未指定なら記事を既定値にする`() {
    assertEquals(
      ContentType.ARTICLE,
      resolveContentType(
        articleOverride = null,
        feedOverride = null,
        folderOverride = null,
      ),
    )
  }
}
