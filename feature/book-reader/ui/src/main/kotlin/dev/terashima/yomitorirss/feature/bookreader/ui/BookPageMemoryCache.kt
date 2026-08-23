package dev.terashima.yomitorirss.feature.bookreader.ui

import dev.terashima.yomitorirss.feature.bookreader.BookPageImage

internal class BookPageMemoryCache(
  private val maxBytes: Int = DEFAULT_PAGE_CACHE_BYTES,
  private val maxEntries: Int = DEFAULT_PAGE_CACHE_ENTRIES,
  private val maxAspectRatioEntries: Int = DEFAULT_ASPECT_RATIO_CACHE_ENTRIES,
) {
  private val pages = LinkedHashMap<Int, BookPageImage>(16, 0.75f, true)
  private val aspectRatios = LinkedHashMap<Int, Float>(16, 0.75f, true)
  private var cachedBytes = 0
  private var fallbackAspectRatio = DEFAULT_VERTICAL_PAGE_ASPECT_RATIO

  init {
    require(maxBytes > 0) { "maxBytes must be positive" }
    require(maxEntries > 0) { "maxEntries must be positive" }
    require(maxAspectRatioEntries > 0) { "maxAspectRatioEntries must be positive" }
  }

  @Synchronized
  fun get(page: Int): BookPageImage? = pages[page]

  @Synchronized
  fun put(page: Int, image: BookPageImage) {
    val aspectRatio = image.width.coerceAtLeast(1).toFloat() / image.height.coerceAtLeast(1)
    aspectRatios[page] = aspectRatio
    fallbackAspectRatio = aspectRatio
    trimAspectRatios()

    pages.remove(page)?.let { cachedBytes -= it.bytes.size }
    if (image.bytes.size > maxBytes) return

    pages[page] = image
    cachedBytes += image.bytes.size
    trimPages()
  }

  @Synchronized
  fun aspectRatio(page: Int): Float = aspectRatios[page] ?: fallbackAspectRatio

  private fun trimPages() {
    val iterator = pages.entries.iterator()
    while ((cachedBytes > maxBytes || pages.size > maxEntries) && iterator.hasNext()) {
      val entry = iterator.next()
      cachedBytes -= entry.value.bytes.size
      iterator.remove()
    }
  }

  private fun trimAspectRatios() {
    val iterator = aspectRatios.entries.iterator()
    while (aspectRatios.size > maxAspectRatioEntries && iterator.hasNext()) {
      iterator.next()
      iterator.remove()
    }
  }
}

internal const val DEFAULT_VERTICAL_PAGE_ASPECT_RATIO = 0.70710677f
private const val DEFAULT_PAGE_CACHE_BYTES = 48 * 1024 * 1024
private const val DEFAULT_PAGE_CACHE_ENTRIES = 16
private const val DEFAULT_ASPECT_RATIO_CACHE_ENTRIES = 256
