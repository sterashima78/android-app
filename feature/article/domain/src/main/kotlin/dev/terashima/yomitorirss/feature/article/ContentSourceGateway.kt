package dev.terashima.yomitorirss.feature.article

data class SourceContentItem(
  val externalId: String?,
  val identityKey: String,
  val url: String,
  val title: String,
  val publishedAt: String,
)

data class ContentSourceSnapshot(
  val id: String,
  val title: String,
  val sourceUrl: String,
)

/** Source Context が Content persistence を操作するための Content-owned command port。 */
interface ContentSourceGateway {
  fun upsertSourceContent(
    source: ContentSourceSnapshot,
    items: List<SourceContentItem>,
    fetchedAt: String,
    insertedReadAt: String? = null,
  )

  fun renameSourceContent(sourceId: String, sourceTitle: String)

  fun detachSourceContent(
    sourceId: String,
    inheritedContentType: ContentType?,
  )
}
