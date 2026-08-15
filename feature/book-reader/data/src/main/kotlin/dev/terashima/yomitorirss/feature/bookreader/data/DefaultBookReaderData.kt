package dev.terashima.yomitorirss.feature.bookreader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dev.terashima.yomitorirss.feature.bookreader.BookDocument
import dev.terashima.yomitorirss.feature.bookreader.BookFormat
import dev.terashima.yomitorirss.feature.bookreader.BookPageImage
import dev.terashima.yomitorirss.feature.bookreader.BookPageSource
import dev.terashima.yomitorirss.feature.bookreader.ReaderMode
import dev.terashima.yomitorirss.feature.bookreader.ReadingDirection
import dev.terashima.yomitorirss.feature.bookreader.ReadingPosition
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultBookPageSourceFactory {
  fun open(document: BookDocument): BookPageSource = when (document.format) {
    BookFormat.ZIP -> ZipBookPageSource(File(document.localPath))
    BookFormat.PDF -> PdfBookPageSource(File(document.localPath))
  }
}

class SharedPreferencesReadingPositionStore(
  context: Context,
) : ReadingPositionStore {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override fun load(bookId: String): ReadingPosition {
    val prefix = keyPrefix(bookId)
    return ReadingPosition(
      pageIndex = preferences.getInt("${prefix}page", 0).coerceAtLeast(0),
      pageOffset = preferences.getInt("${prefix}offset", 0).coerceAtLeast(0),
      mode = preferences.getString("${prefix}mode", null)
        ?.let { runCatching { ReaderMode.valueOf(it) }.getOrNull() }
        ?: ReaderMode.PAGED,
      direction = preferences.getString("${prefix}direction", null)
        ?.let { runCatching { ReadingDirection.valueOf(it) }.getOrNull() }
        ?: ReadingDirection.RIGHT_TO_LEFT,
    )
  }

  override fun save(bookId: String, position: ReadingPosition) {
    val prefix = keyPrefix(bookId)
    preferences.edit()
      .putInt("${prefix}page", position.pageIndex.coerceAtLeast(0))
      .putInt("${prefix}offset", position.pageOffset.coerceAtLeast(0))
      .putString("${prefix}mode", position.mode.name)
      .putString("${prefix}direction", position.direction.name)
      .apply()
  }

  private fun keyPrefix(bookId: String): String = "book:$bookId:"

  private companion object {
    const val PREFERENCES_NAME = "book_reader_position"
  }
}

private class ZipBookPageSource(
  file: File,
) : BookPageSource {
  private val zipFile = ZipFile(file)
  private val pages = zipFile.entries().asSequence()
    .filterNot { it.isDirectory }
    .filter { entry -> entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS }
    .sortedWith(compareByNaturalName { it.name })
    .toList()

  override val pageCount: Int = pages.size

  init {
    require(pageCount > 0) { "ZIP内に表示できる画像がありません" }
  }

  override suspend fun loadPage(index: Int, targetWidth: Int): BookPageImage = withContext(Dispatchers.IO) {
    val entry = pages.getOrNull(index) ?: error("ページが見つかりません: $index")
    val bytes = zipFile.getInputStream(entry).use { it.readBytes() }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth > 0 && options.outHeight > 0) { "画像を読み込めません: ${entry.name}" }
    BookPageImage(
      bytes = bytes,
      width = options.outWidth,
      height = options.outHeight,
    )
  }

  override fun close() {
    zipFile.close()
  }
}

private class PdfBookPageSource(
  file: File,
) : BookPageSource {
  private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  private val renderer = PdfRenderer(descriptor)
  private val rendererMutex = Mutex()

  override val pageCount: Int = renderer.pageCount

  init {
    require(pageCount > 0) { "PDFにページがありません" }
  }

  override suspend fun loadPage(index: Int, targetWidth: Int): BookPageImage = withContext(Dispatchers.IO) {
    rendererMutex.withLock {
      renderer.openPage(index).use { page ->
        val width = targetWidth.coerceIn(MIN_PDF_WIDTH, MAX_PDF_WIDTH)
        val height = (width.toDouble() * page.height / page.width)
          .toInt()
          .coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
          bitmap.eraseColor(android.graphics.Color.WHITE)
          page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PDFページの変換に失敗しました" }
            output.toByteArray()
          }
          BookPageImage(bytes = bytes, width = width, height = height)
        } finally {
          bitmap.recycle()
        }
      }
    }
  }

  override fun close() {
    renderer.close()
    descriptor.close()
  }
}

private fun <T> compareByNaturalName(selector: (T) -> String): Comparator<T> = Comparator { left, right ->
  naturalCompare(selector(left), selector(right))
}

internal fun naturalCompare(left: String, right: String): Int {
  var leftIndex = 0
  var rightIndex = 0
  while (leftIndex < left.length && rightIndex < right.length) {
    val leftChar = left[leftIndex]
    val rightChar = right[rightIndex]
    if (leftChar.isDigit() && rightChar.isDigit()) {
      val leftStart = leftIndex
      val rightStart = rightIndex
      while (leftIndex < left.length && left[leftIndex].isDigit()) leftIndex++
      while (rightIndex < right.length && right[rightIndex].isDigit()) rightIndex++
      val leftNumber = left.substring(leftStart, leftIndex).trimStart('0')
      val rightNumber = right.substring(rightStart, rightIndex).trimStart('0')
      val lengthResult = leftNumber.length.compareTo(rightNumber.length)
      if (lengthResult != 0) return@Comparator lengthResult
      val numberResult = leftNumber.compareTo(rightNumber)
      if (numberResult != 0) return@Comparator numberResult
      continue
    }
    val charResult = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
    if (charResult != 0) return@Comparator charResult
    leftIndex++
    rightIndex++
  }
  left.length.compareTo(right.length)
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private const val MIN_PDF_WIDTH = 720
private const val MAX_PDF_WIDTH = 2200
