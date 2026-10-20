package dev.terashima.yomitorirss.feature.video.ui

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import dev.terashima.yomitorirss.core.network.TrafficTelemetry

/** Counts actual video transfer bytes, including transfers ending before playback completes. */
@UnstableApi
internal class TrafficVideoDataSourceFactory(
  private val delegate: DataSource.Factory,
) : DataSource.Factory {
  override fun createDataSource(): DataSource = delegate.createDataSource().also { dataSource ->
    dataSource.addTransferListener(object : TransferListener {
      private var receivedBytes = 0L

      override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

      override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        receivedBytes = 0L
      }

      override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int,
      ) {
        if (isNetwork) receivedBytes += bytesTransferred
      }

      override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (isNetwork) TrafficTelemetry.record("video", receivedBytes, 0L)
        receivedBytes = 0L
      }
    })
  }
}
