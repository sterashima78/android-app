package dev.terashima.yomitorirss.feature.article

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentRetentionPolicyTest {
  private val policy = ContentRetentionPolicy()

  @Test
  fun `既読Contentの保持期限は30日前`() {
    val now = Instant.parse("2026-08-19T00:00:00Z")

    assertEquals(
      Instant.parse("2026-07-20T00:00:00Z"),
      policy.expiryCutoff(now),
    )
  }

  @Test
  fun `別Contextに保護されたContentは削除候補から除外する`() {
    val actual = policy.deletableContentIds(
      expiredCandidateIds = setOf("expired", "protected"),
      protectedContentIds = setOf("protected"),
    )

    assertEquals(setOf("expired"), actual)
  }
}
