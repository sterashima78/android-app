package dev.terashima.yomitorirss.feature.article

import java.time.Duration
import java.time.Instant

/** Summary 等の別 Context が Content 削除を保護する必要があるかを返す query port。 */
interface ContentRetentionProtectionQuery {
  suspend fun protectedContentIds(contentIds: Set<String>): Set<String>
}

/** Content の保持期間と、外部 Context に保護された Content を削除しない規則を表す Domain Service。 */
class ContentRetentionPolicy(
  private val readRetention: Duration = Duration.ofDays(DEFAULT_READ_RETENTION_DAYS),
) {
  fun expiryCutoff(now: Instant): Instant = now.minus(readRetention)

  fun deletableContentIds(
    expiredCandidateIds: Set<String>,
    protectedContentIds: Set<String>,
  ): Set<String> = expiredCandidateIds - protectedContentIds

  private companion object {
    const val DEFAULT_READ_RETENTION_DAYS = 30L
  }
}
