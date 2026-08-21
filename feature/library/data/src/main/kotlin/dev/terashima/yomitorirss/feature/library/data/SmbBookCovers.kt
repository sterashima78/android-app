package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import dev.terashima.yomitorirss.feature.library.SmbBookFormat
import java.io.File
import java.io.InputStream
import java.util.EnumSet
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal fun existingSmbBookCoverUrl(
  context: Context,
  sourceId: String,
  size: Long,
  modifiedAt: Long,
): String? {
  val coverFile = smbBookCoverFile(context, sourceId, size, modifiedAt)
  if (!coverFile.isFile || coverFile.length() <= 0L) return null
  coverFile.setLastModified(System.currentTimeMillis())
  return Uri.fromFile(coverFile).toString()
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveSmbBookCover(
  context: Context,
  share: DiskShare,
  remotePath: String,
  sourceId: String,
  size: Long,
  modifiedAt: Long,
  format: SmbBookFormat,
  cachedBookFile: File,
): String? {
  existingSmbBookCoverUrl(context, sourceId, size, modifiedAt)?.let { return it }
  if (!cachedBookFile.isFile || cachedBookFile.length() != size) return null
  val coverFile = smbBookCoverFile(context, sourceId, size, modifiedAt)
  val generated = generateLocalBookCover(cachedBookFile, format, coverFile)
  return coverFile.takeIf { generated && it.isFile && it.length() > 0L }
    ?.let(Uri::fromFile)
    ?.toString()
}

internal fun prefetchRemoteSmbZipCover(
  context: Context,
  share: DiskShare,
  remotePath: String,
  sourceId: String,
  size: Long,
  modifiedAt: Long,
): String? {
  existingSmbBookCoverUrl(context, sourceId, size, modifiedAt)?.let { return it }
  val coverFile = smbBookCoverFile(context, sourceId, size, modifiedAt)
  val generated = generateRemoteZipCover(share, remotePath, coverFile)
  return coverFile.takeIf { generated && it.isFile && it.length() > 0L }
    ?.let(Uri::fromFile)
    ?.toString()
}

internal fun ensureSmbBookCoverFromLocal(
  context: Context,
  sourceId: String,
  size: Long,
  modifiedAt: Long,
  format: SmbBookFormat,
  localBookFile: File,
): String? {
  existingSmbBookCoverUrl(context, sourceId, size, modifiedAt)?.let { return it }
  val coverFile = smbBookCoverFile(context, sourceId, size, modifiedAt)
  val ready = generateLocalBookCover(localBookFile, format, coverFile)
  return coverFile.takeIf { ready && it.isFile && it.length() > 0L }
    ?.let(Uri::fromFile)
    ?.toString()
}

internal fun cleanupSmbBookCovers(context: Context, validCoverUrls: Collection<String>) {
  val validPaths = validCoverUrls.mapNotNull { value ->
    runCatching { Uri.parse(value) }
      .getOrNull()
      ?.takeIf { it.scheme == "file" }
      ?.path
  }.toSet()
  smbBookCoverRoot(context).listFiles()?.forEach { file ->
    if (file.absolutePath !in validPaths) file.delete()
  }
}

internal fun deleteSmbBookCovers(context: Context, sourceIds: Collection<String>) {
  if (sourceIds.isEmpty()) return
  smbBookCoverRoot(context).listFiles()?.forEach { file ->
    if (sourceIds.any { sourceId -> file.name.startsWith("$sourceId-") }) file.delete()
  }
}

internal data class SmbCoverCacheEntry(
  val path: String,
  val size: Long,
  val lastModified: Long,
)

internal fun smbCoverCachePathsToEvict(
  entries: List<SmbCoverCacheEntry>,
  maxBytes: Long = COVER_CACHE_MAX_BYTES,
  protectedPath: String? = null,
): List<String> {
  var totalBytes = entries.sumOf { it.size.coerceAtLeast(0L) }
  if (totalBytes <= maxBytes) return emptyList()

  val evicted = mutableListOf<String>()
  entries
    .asSequence()
    .filter { it.path != protectedPath }
    .sortedWith(compareBy<SmbCoverCacheEntry>({ it.lastModified }, { it.path }))
    .forEach { entry ->
      if (totalBytes <= maxBytes) return@forEach
      evicted += entry.path
      totalBytes -= entry.size.coerceAtLeast(0L)
    }
  return evicted
}

internal fun trimSmbBookCoverCache(
  context: Context,
  protectedUrl: String? = null,
): List<String> {
  val protectedPath = protectedUrl
    ?.let { runCatching { Uri.parse(it) }.getOrNull() }
    ?.takeIf { it.scheme == "file" }
    ?.path
  val root = smbBookCoverRoot(context)
  val files = root.listFiles()
    ?.filter { file -> file.isFile && !file.name.endsWith(".tmp") }
    .orEmpty()
  val pathsToEvict = smbCoverCachePathsToEvict(
    entries = files.map { file ->
      SmbCoverCacheEntry(
        path = file.absolutePath,
        size = file.length(),
        lastModified = file.lastModified(),
      )
    },
    protectedPath = protectedPath,
  ).toSet()
  if (pathsToEvict.isEmpty()) return emptyList()

  return files.mapNotNull { file ->
    if (file.absolutePath !in pathsToEvict) return@mapNotNull null
    val url = Uri.fromFile(file).toString()
    url.takeIf { file.delete() }
  }
}

private fun smbBookCoverFile(
  context: Context,
  sourceId: String,
  size: Long,
  modifiedAt: Long,
): File = File(smbBookCoverRoot(context), "$sourceId-$size-$modifiedAt.jpg")

private fun smbBookCoverRoot(context: Context): File =
  File(context.applicationContext.cacheDir, COVER_CACHE_DIRECTORY).apply { mkdirs() }

private fun generateRemoteZipCover(
  share: DiskShare,
  remotePath: String,
  coverFile: File,
): Boolean = runCatching {
  share.openFile(
    remotePath,
    EnumSet.of(AccessMask.FILE_READ_DATA),
    null,
    SMB2ShareAccess.ALL,
    SMB2CreateDisposition.FILE_OPEN,
    null,
  ).use { remoteFile ->
    remoteFile.getInputStream().use { input ->
      val bytes = extractFirstZipImage(input) ?: return@runCatching false
      saveImageCover(bytes, coverFile)
    }
  }
}.getOrDefault(false)

private fun generateLocalBookCover(
  bookFile: File,
  format: SmbBookFormat,
  coverFile: File,
): Boolean = when (format) {
  SmbBookFormat.ZIP -> generateLocalZipCover(bookFile, coverFile)
  SmbBookFormat.PDF -> generateLocalPdfCover(bookFile, coverFile)
}

private fun generateLocalZipCover(bookFile: File, coverFile: File): Boolean = runCatching {
  ZipFile(bookFile).use { zip ->
    val entry = zip.entries().asSequence()
      .filterNot { it.isDirectory }
      .filter { isImageFile(it.name) }
      .sortedWith(Comparator { left, right -> naturalCompare(left.name, right.name) })
      .firstOrNull()
      ?: return@runCatching false
    val bytes = zip.getInputStream(entry).use { input ->
      LimitedInputStream(input, MAX_COVER_SOURCE_BYTES).readBytes()
    }
    saveImageCover(bytes, coverFile)
  }
}.getOrDefault(false)

private fun generateLocalPdfCover(bookFile: File, coverFile: File): Boolean = runCatching {
  ParcelFileDescriptor.open(bookFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
    PdfRenderer(descriptor).use { renderer ->
      if (renderer.pageCount <= 0) return@runCatching false
      renderer.openPage(0).use { page ->
        val width = PDF_COVER_WIDTH
        val height = (width.toDouble() * page.height / page.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        saveBitmapCover(bitmap, coverFile)
      }
    }
  }
}.getOrDefault(false)

internal fun extractFirstZipImage(
  input: InputStream,
  maxBytes: Long = SMB_ZIP_COVER_SCAN_MAX_BYTES,
): ByteArray? = runCatching {
  var result: ByteArray? = null
  ZipInputStream(LimitedInputStream(input, maxBytes)).use { zip ->
    while (result == null) {
      val entry = zip.nextEntry ?: break
      if (!entry.isDirectory && isImageFile(entry.name)) {
        result = zip.readBytes()
      }
      zip.closeEntry()
    }
  }
  result
}.getOrNull()

private fun saveImageCover(bytes: ByteArray, coverFile: File): Boolean {
  if (bytes.isEmpty()) return false
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

  val options = BitmapFactory.Options().apply {
    inSampleSize = coverSampleSize(bounds.outWidth, bounds.outHeight)
  }
  val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return false
  return saveBitmapCover(bitmap, coverFile)
}

private fun saveBitmapCover(bitmap: Bitmap, coverFile: File): Boolean {
  val maxDimension = maxOf(bitmap.width, bitmap.height)
  val outputBitmap = if (maxDimension > COVER_MAX_DIMENSION) {
    val scale = COVER_MAX_DIMENSION.toDouble() / maxDimension
    Bitmap.createScaledBitmap(
      bitmap,
      (bitmap.width * scale).toInt().coerceAtLeast(1),
      (bitmap.height * scale).toInt().coerceAtLeast(1),
      true,
    )
  } else {
    bitmap
  }
  val temp = File(coverFile.parentFile, "${coverFile.name}.tmp")
  return try {
    temp.outputStream().buffered().use { output ->
      check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, output)) {
        "SMB書籍の表紙を保存できませんでした"
      }
    }
    if (coverFile.exists()) coverFile.delete()
    if (!temp.renameTo(coverFile)) {
      temp.copyTo(coverFile, overwrite = true)
      temp.delete()
    }
    coverFile.setLastModified(System.currentTimeMillis())
    coverFile.isFile && coverFile.length() > 0L
  } finally {
    temp.delete()
    if (outputBitmap !== bitmap) outputBitmap.recycle()
    bitmap.recycle()
  }
}

private fun coverSampleSize(width: Int, height: Int): Int {
  var sampleSize = 1
  while (width / sampleSize > COVER_DECODE_MAX_DIMENSION || height / sampleSize > COVER_DECODE_MAX_DIMENSION) {
    sampleSize *= 2
  }
  return sampleSize
}

private fun isImageFile(name: String): Boolean =
  name.substringAfterLast('.', "").lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

private fun naturalCompare(left: String, right: String): Int {
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
      if (lengthResult != 0) return lengthResult
      val numberResult = leftNumber.compareTo(rightNumber)
      if (numberResult != 0) return numberResult
      continue
    }
    val charResult = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
    if (charResult != 0) return charResult
    leftIndex++
    rightIndex++
  }
  return left.length.compareTo(right.length)
}

private class LimitedInputStream(
  private val delegate: InputStream,
  private var remaining: Long,
) : InputStream() {
  override fun read(): Int {
    if (remaining <= 0L) return -1
    val value = delegate.read()
    if (value >= 0) remaining -= 1
    return value
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (remaining <= 0L) return -1
    val allowed = minOf(length.toLong(), remaining).toInt()
    val count = delegate.read(buffer, offset, allowed)
    if (count > 0) remaining -= count
    return count
  }

  override fun close() {
    delegate.close()
  }
}

internal const val SMB_ZIP_COVER_SCAN_MAX_BYTES = 64L * 1024 * 1024
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private const val COVER_CACHE_DIRECTORY = "smb-book-covers"
private const val COVER_MAX_DIMENSION = 640
private const val COVER_DECODE_MAX_DIMENSION = 1600
private const val COVER_JPEG_QUALITY = 85
private const val PDF_COVER_WIDTH = 640
private const val MAX_COVER_SOURCE_BYTES = 32L * 1024 * 1024
private const val COVER_CACHE_MAX_BYTES = 200L * 1024 * 1024
