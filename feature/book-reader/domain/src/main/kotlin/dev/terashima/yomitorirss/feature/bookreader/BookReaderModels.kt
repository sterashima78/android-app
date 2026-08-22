package dev.terashima.yomitorirss.feature.bookreader

enum class BookFormat {
  ZIP,
  PDF,
}

enum class ReaderMode {
  PAGED,
  VERTICAL,
}

enum class ReadingDirection {
  RIGHT_TO_LEFT,
  LEFT_TO_RIGHT,
}

data class BookDocument(
  val id: String,
  val title: String,
  val format: BookFormat,
  val localPath: String,
)

data class BookPageImage(
  val bytes: ByteArray,
  val width: Int,
  val height: Int,
)

data class ReadingPosition(
  val pageIndex: Int = 0,
  val pageOffset: Int = 0,
  val mode: ReaderMode = ReaderMode.PAGED,
  val direction: ReadingDirection = ReadingDirection.RIGHT_TO_LEFT,
)

interface BookPageSource : AutoCloseable {
  val pageCount: Int

  suspend fun loadPage(
    index: Int,
    targetWidth: Int,
  ): BookPageImage

  override fun close() = Unit
}

fun interface BookPageSourceFactory {
  fun open(document: BookDocument): BookPageSource
}

interface ReadingPositionStore {
  fun load(bookId: String): ReadingPosition

  fun save(bookId: String, position: ReadingPosition)
}
