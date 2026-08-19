package dev.terashima.yomitorirss.feature.article

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentClassificationServiceTest {
  private val service = ContentClassificationService()

  @Test
  fun `Content自身のoverrideを最優先する`() {
    val actual = service.resolve(
      contentOverride = ContentType.ARTICLE,
      sourceOverrides = SourceContentTypeOverrides(
        sourceOverride = ContentType.COMIC,
        sourceContainerOverride = ContentType.COMIC,
      ),
    )

    assertEquals(ContentType.ARTICLE, actual)
  }

  @Test
  fun `Sourceのoverrideをcontainerより優先する`() {
    val actual = service.resolve(
      contentOverride = null,
      sourceOverrides = SourceContentTypeOverrides(
        sourceOverride = ContentType.ARTICLE,
        sourceContainerOverride = ContentType.COMIC,
      ),
    )

    assertEquals(ContentType.ARTICLE, actual)
  }

  @Test
  fun `overrideがなければARTICLEを返す`() {
    assertEquals(ContentType.ARTICLE, service.resolve(null, null))
  }
}
