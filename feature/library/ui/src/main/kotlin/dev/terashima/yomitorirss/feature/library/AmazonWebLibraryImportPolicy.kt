package dev.terashima.yomitorirss.feature.library

import java.net.URI

internal class ImportChunkAccumulator(
  private val maxBytes: Int = MAX_WEB_LIBRARY_EXPORT_BYTES,
) {
  private var sessionId: String? = null
  private var expectedChunks: Int = 0
  private var declaredByteLength: Int = 0
  private var chunks: Array<String?> = emptyArray()
  private var receivedBytes: Int = 0

  fun start(sessionId: String, totalChunks: Int, declaredByteLength: Int) {
    require(sessionId.isNotBlank()) { "Web Library セッションが不正です" }
    require(totalChunks in 1..MAX_WEB_LIBRARY_CHUNKS) { "Web Library の分割数が不正です" }
    require(declaredByteLength in 1..maxBytes) { "Web Library の取得データが大きすぎます" }
    this.sessionId = sessionId
    this.expectedChunks = totalChunks
    this.declaredByteLength = declaredByteLength
    this.chunks = arrayOfNulls(totalChunks)
    this.receivedBytes = 0
  }

  fun add(sessionId: String, index: Int, totalChunks: Int, data: String) {
    require(this.sessionId == sessionId) { "Web Library セッションが一致しません" }
    require(totalChunks == expectedChunks) { "Web Library の分割数が一致しません" }
    require(index in chunks.indices) { "Web Library の分割位置が不正です" }
    val existing = chunks[index]
    if (existing != null) {
      require(existing == data) { "Web Library の分割データが競合しました" }
      return
    }
    val chunkBytes = data.toByteArray(Charsets.UTF_8).size
    require(receivedBytes + chunkBytes <= maxBytes) { "Web Library の取得データが大きすぎます" }
    chunks[index] = data
    receivedBytes += chunkBytes
  }

  fun finish(sessionId: String): String {
    require(this.sessionId == sessionId) { "Web Library セッションが一致しません" }
    require(chunks.isNotEmpty() && chunks.all { it != null }) { "Web Library の取得データが不足しています" }
    val result = buildString {
      chunks.forEach { append(it) }
    }
    val actualByteLength = result.toByteArray(Charsets.UTF_8).size
    require(actualByteLength == declaredByteLength) { "Web Library の取得データが破損しています" }
    reset()
    return result
  }

  fun reset() {
    sessionId = null
    expectedChunks = 0
    declaredByteLength = 0
    chunks = emptyArray()
    receivedBytes = 0
  }
}

internal fun isTrustedAmazonImportNavigation(url: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  val host = uri.host?.lowercase() ?: return false
  return host == "amazon.co.jp" || host.endsWith(".amazon.co.jp") ||
    host == "audible.co.jp" || host.endsWith(".audible.co.jp")
}

internal fun isKindleWebLibraryPage(url: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  return uri.scheme.equals("https", ignoreCase = true) &&
    uri.host.equals("read.amazon.co.jp", ignoreCase = true) &&
    uri.path.orEmpty().startsWith("/kindle-library")
}

internal fun isAudibleLibraryPage(url: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  return uri.scheme.equals("https", ignoreCase = true) &&
    uri.host.equals("www.audible.co.jp", ignoreCase = true) &&
    uri.path.orEmpty().startsWith("/library/titles")
}

internal fun isAudibleCatalogApiPage(url: String): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  return uri.scheme.equals("https", ignoreCase = true) &&
    uri.host.equals("api.audible.co.jp", ignoreCase = true) &&
    uri.path.orEmpty().startsWith("/1.0/catalog/products")
}

internal const val MAX_WEB_LIBRARY_EXPORT_BYTES = 25 * 1024 * 1024
private const val MAX_WEB_LIBRARY_CHUNKS = 2048
