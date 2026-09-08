package dev.terashima.yomitorirss.feature.video.ui

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import dev.terashima.yomitorirss.feature.video.VideoByteSourceFactory

internal class SmbVideoDataSource(
  private val sourcePool: SmbVideoSourcePool,
) : BaseDataSource(true) {
  private var lease: SmbVideoSourcePool.Lease? = null
  private var uri: Uri? = null
  private var position = 0L
  private var bytesRemaining = 0L
  private var opened = false

  override fun open(dataSpec: DataSpec): Long {
    transferInitializing(dataSpec)
    val sourceId = dataSpec.uri.getQueryParameter(SOURCE_ID_PARAMETER)
      ?.takeIf(String::isNotBlank)
      ?: error("SMB動画のsourceIdがありません")
    val openedLease = sourcePool.acquire(sourceId)
    try {
      position = dataSpec.position
      require(position <= openedLease.length) { "SMB動画の再生位置がファイル範囲外です" }
      val available = openedLease.length - position
      bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
        available
      } else {
        minOf(dataSpec.length, available)
      }
      lease = openedLease
      uri = dataSpec.uri
      opened = true
      transferStarted(dataSpec)
      return bytesRemaining
    } catch (error: Throwable) {
      openedLease.close()
      position = 0L
      bytesRemaining = 0L
      throw error
    }
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
    val openedLease = lease ?: return C.RESULT_END_OF_INPUT
    val requested = minOf(length.toLong(), bytesRemaining).toInt()
    val read = try {
      openedLease.read(position, buffer, offset, requested)
    } catch (error: Throwable) {
      openedLease.invalidate()
      openedLease.close()
      lease = null
      throw error
    }
    if (read <= 0) return C.RESULT_END_OF_INPUT
    position += read
    bytesRemaining -= read
    bytesTransferred(read)
    return read
  }

  override fun getUri(): Uri? = uri

  override fun close() {
    lease?.close()
    lease = null
    uri = null
    position = 0L
    bytesRemaining = 0L
    if (opened) {
      opened = false
      transferEnded()
    }
  }

  class Factory(
    byteSourceFactory: VideoByteSourceFactory,
  ) : DataSource.Factory, AutoCloseable {
    private val sourcePool = SmbVideoSourcePool(byteSourceFactory)

    override fun createDataSource(): DataSource = SmbVideoDataSource(sourcePool)

    override fun close() = sourcePool.close()
  }

  companion object {
    private const val SOURCE_ID_PARAMETER = "sourceId"

    fun mediaUri(sourceId: String): Uri = Uri.Builder()
      .scheme("mosaic-video")
      .authority("smb")
      .appendQueryParameter(SOURCE_ID_PARAMETER, sourceId)
      .build()
  }
}
