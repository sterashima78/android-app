package dev.terashima.yomitorirss.feature.library.data

import java.io.InputStream

internal class KindleSeriesMetadataScanner {
  fun scan(fileName: String?, input: InputStream): Map<String, KindleSeriesMetadata> =
    KindleSeriesZipReader().read(fileName, input)
}
