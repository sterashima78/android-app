package dev.terashima.yomitorirss.feature.bookreader.ui

import dev.terashima.yomitorirss.feature.bookreader.BookPageImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BookPageMemoryCacheTest {
  @Test
  fun `recent pages remain cached`() {
    val cache = BookPageMemoryCache(maxBytes = 1024, maxEntries = 2)
    val first = image(byteCount = 100, width = 100, height = 200)
    val second = image(byteCount = 100, width = 100, height = 200)

    cache.put(0, first)
    cache.put(1, second)

    assertSame(first, cache.get(0))
    assertSame(second, cache.get(1))
  }

  @Test
  fun `least recently used page is evicted`() {
    val cache = BookPageMemoryCache(maxBytes = 1024, maxEntries = 2)
    val first = image(byteCount = 100, width = 100, height = 200)
    val second = image(byteCount = 100, width = 100, height = 200)
    val third = image(byteCount = 100, width = 100, height = 200)

    cache.put(0, first)
    cache.put(1, second)
    assertSame(first, cache.get(0))
    cache.put(2, third)

    assertSame(first, cache.get(0))
    assertNull(cache.get(1))
    assertSame(third, cache.get(2))
  }

  @Test
  fun `page geometry remains after image eviction`() {
    val cache = BookPageMemoryCache(maxBytes = 1, maxEntries = 1)

    cache.put(4, image(byteCount = 2, width = 800, height = 1200))

    assertNull(cache.get(4))
    assertEquals(2f / 3f, cache.aspectRatio(4), 0.0001f)
  }

  @Test
  fun `unknown page uses most recently learned geometry`() {
    val cache = BookPageMemoryCache(maxBytes = 1024, maxEntries = 2)

    cache.put(1, image(byteCount = 100, width = 900, height = 1200))

    assertEquals(0.75f, cache.aspectRatio(2), 0.0001f)
  }

  @Test
  fun `page geometry cache evicts least recently used metadata`() {
    val cache = BookPageMemoryCache(
      maxBytes = 1,
      maxEntries = 1,
      maxAspectRatioEntries = 2,
    )

    cache.put(0, image(byteCount = 2, width = 500, height = 1000))
    cache.put(1, image(byteCount = 2, width = 600, height = 1000))
    assertEquals(0.5f, cache.aspectRatio(0), 0.0001f)
    cache.put(2, image(byteCount = 2, width = 800, height = 1000))

    assertEquals(0.5f, cache.aspectRatio(0), 0.0001f)
    assertEquals(0.8f, cache.aspectRatio(1), 0.0001f)
  }

  private fun image(
    byteCount: Int,
    width: Int,
    height: Int,
  ) = BookPageImage(
    bytes = ByteArray(byteCount),
    width = width,
    height = height,
  )
}
