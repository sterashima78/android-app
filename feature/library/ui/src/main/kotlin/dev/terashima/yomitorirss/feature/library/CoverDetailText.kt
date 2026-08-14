package dev.terashima.yomitorirss.feature.library

internal fun coverDetailText(item: LibraryCoverAcquisitionItem): String =
  "${item.source.name} | ${item.sourceId} | ${item.state.name} | ${item.provider.orEmpty()}"
