package dev.terashima.yomitorirss.feature.library

internal fun coverReportLine(item: LibraryCoverAcquisitionItem): String =
  listOf(
    item.source.name,
    item.sourceId,
    item.title,
    item.state.name,
    item.provider.orEmpty(),
    item.lastAttemptAtEpochMillis?.toString().orEmpty(),
  ).joinToString("\t")
