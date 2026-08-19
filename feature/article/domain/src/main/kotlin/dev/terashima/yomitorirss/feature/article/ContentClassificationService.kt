package dev.terashima.yomitorirss.feature.article

/** Source context が Content classification のために公開する最小限の override 情報。 */
data class SourceContentTypeOverrides(
  val sourceOverride: ContentType?,
  val sourceContainerOverride: ContentType?,
)

/**
 * Content context が上流 Source context へ要求する分類情報の query port。
 *
 * sourceId の具体的な意味（現在は RSS feed id）や永続化構造は実装側が所有する。
 */
interface ContentClassificationSourceQuery {
  suspend fun findOverrides(sourceIds: Set<String>): Map<String, SourceContentTypeOverrides>
}

/** 永続状態を持たず、Content と Source の override から実効種別を決定する Domain Service。 */
class ContentClassificationService {
  fun resolve(
    contentOverride: ContentType?,
    sourceOverrides: SourceContentTypeOverrides?,
  ): ContentType =
    contentOverride
      ?: sourceOverrides?.sourceOverride
      ?: sourceOverrides?.sourceContainerOverride
      ?: ContentType.ARTICLE
}
