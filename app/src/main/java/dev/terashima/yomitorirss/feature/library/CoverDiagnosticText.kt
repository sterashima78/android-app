package dev.terashima.yomitorirss.feature.library

internal fun coverDiagnosticText(item: LibraryCoverAcquisitionItem): String =
  listOf(
    "source=${item.source.name}",
    "sourceId=${item.sourceId}",
    "state=${item.state.name}",
    "provider=${item.provider.orEmpty()}",
    "lastAttempt=${item.lastAttemptAtEpochMillis?.toString().orEmpty()}",
  ).joinToString("\n")
