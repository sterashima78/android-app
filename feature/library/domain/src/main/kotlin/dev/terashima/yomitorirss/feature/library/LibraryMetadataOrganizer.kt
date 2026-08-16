package dev.terashima.yomitorirss.feature.library

import java.util.concurrent.CancellationException

data class LibrarySeriesReorganizationResult(
  val total: Int,
  val updated: Int,
  val failed: Int,
)

class LibraryMetadataOrganizer(
  private val repository: LibraryOrganizationRepository,
  private val suggester: LibraryOrganizationSuggester,
) {
  suspend fun reorganizeSeries(books: List<LibraryBook>): LibrarySeriesReorganizationResult {
    val targets = books.distinctBy(LibraryBook::organizationKey)
    require(targets.isNotEmpty()) { "再整理するシリーズの蔵書がありません" }
    require(targets.all { it.series != null }) { "シリーズが設定された蔵書だけ再整理できます" }
    val targetSeries = requireNotNull(targets.first().series)
    require(targets.all { sameSeries(targetSeries, it.series) }) {
      "同一シリーズの蔵書だけまとめて再整理できます"
    }

    val snapshot = repository.snapshot()
    val committedDrafts = mutableMapOf<LibraryBookKey, LibraryOrganizationDraft>()
    var updated = 0
    var failed = 0

    targets.forEach { book ->
      try {
        val suggestion = suggester.suggest(
          book = book,
          existingTags = taxonomyNames(
            snapshot.tags.map(LibraryOrganizationTag::name),
            committedDrafts.values.flatMap(LibraryOrganizationDraft::tagNames),
          ),
          existingCollections = taxonomyNames(
            snapshot.collections.map(LibraryCollection::name),
            committedDrafts.values.flatMap(LibraryOrganizationDraft::collectionNames),
          ),
          seriesContext = seriesContextFor(book, targets, snapshot, committedDrafts),
        )
        val current = snapshot.organizationFor(book)
        val draft = LibraryOrganizationDraft(
          tagNames = suggestion.tagNames,
          collectionNames = suggestion.collectionNames,
          readingStatus = current.readingStatus,
        )
        repository.save(book = book, draft = draft)
        committedDrafts[book.organizationKey()] = draft
        updated += 1
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Throwable) {
        failed += 1
      }
    }

    return LibrarySeriesReorganizationResult(
      total = targets.size,
      updated = updated,
      failed = failed,
    )
  }
}

internal fun seriesContextForMetadataReorganization(
  book: LibraryBook,
  books: List<LibraryBook>,
  snapshot: LibraryOrganizationSnapshot,
): LibraryOrganizationSeriesContext? = seriesContextFor(
  book = book,
  books = books,
  snapshot = snapshot,
  committedDrafts = emptyMap(),
)

private fun seriesContextFor(
  book: LibraryBook,
  books: List<LibraryBook>,
  snapshot: LibraryOrganizationSnapshot,
  committedDrafts: Map<LibraryBookKey, LibraryOrganizationDraft>,
): LibraryOrganizationSeriesContext? {
  val series = book.series ?: return null
  val peerMetadata = books.asSequence()
    .filter { it.organizationKey() != book.organizationKey() }
    .filter { sameSeries(series, it.series) }
    .map { peer ->
      committedDrafts[peer.organizationKey()]?.let { draft ->
        MetadataNames(draft.tagNames, draft.collectionNames)
      } ?: snapshot.organizationFor(peer).let { organization ->
        MetadataNames(
          tagNames = organization.tags.map(LibraryOrganizationTag::name),
          collectionNames = organization.collections.map(LibraryCollection::name),
        )
      }
    }
    .toList()

  val tagNames = peerMetadata
    .flatMap(MetadataNames::tagNames)
    .distinctBy(::normalizeMetadataName)
    .take(MAX_SERIES_CONTEXT_TAGS)
  val collectionNames = peerMetadata
    .flatMap(MetadataNames::collectionNames)
    .distinctBy(::normalizeMetadataName)
    .take(MAX_SERIES_CONTEXT_COLLECTIONS)

  if (tagNames.isEmpty() && collectionNames.isEmpty()) return null
  return LibraryOrganizationSeriesContext(
    tagNames = tagNames,
    collectionNames = collectionNames,
  )
}

private data class MetadataNames(
  val tagNames: List<String>,
  val collectionNames: List<String>,
)

private fun taxonomyNames(
  persisted: List<String>,
  committed: List<String>,
): List<String> = (persisted + committed).distinctBy(::normalizeMetadataName)

private fun sameSeries(left: LibrarySeries, right: LibrarySeries?): Boolean {
  right ?: return false
  val leftId = left.id?.trim()?.takeIf(String::isNotEmpty)
  val rightId = right.id?.trim()?.takeIf(String::isNotEmpty)
  if (leftId != null && rightId != null) return leftId.equals(rightId, ignoreCase = true)

  val leftName = left.name.trim()
  val rightName = right.name.trim()
  if (leftName.isEmpty() || rightName.isEmpty()) return false
  return leftName.equals(rightName, ignoreCase = true)
}

private fun normalizeMetadataName(value: String): String = value.trim().lowercase()

private const val MAX_SERIES_CONTEXT_TAGS = 20
private const val MAX_SERIES_CONTEXT_COLLECTIONS = 10
