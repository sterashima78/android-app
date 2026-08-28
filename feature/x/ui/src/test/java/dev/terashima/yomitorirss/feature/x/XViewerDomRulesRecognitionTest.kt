package dev.terashima.yomitorirss.feature.x

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XViewerDomRulesRecognitionTest {
  @Test
  fun `固定タブ表示ルールは aria-selected なしの role tab を保存できる`() {
    val fingerprints =
      "[{\"kind\":\"TEXT\",\"value\":\"a\"},{\"kind\":\"TEXT\",\"value\":\"b\"},{\"kind\":\"TEXT\",\"value\":\"c\"}]"
    val json =
      "{\"kind\":\"KEEP_MATCHING_ITEMS\",\"pagePath\":\"/home\"," +
        "\"containerSelector\":\"[data-testid=\\\"primaryColumn\\\"] [role=\\\"tablist\\\"]\"," +
        "\"itemSelector\":\"[role=\\\"tab\\\"]\"," +
        "\"targetKind\":\"FINGERPRINT_SET\",\"targetValue\":" +
        "\"[{\\\"kind\\\":\\\"TEXT\\\",\\\"value\\\":\\\"a\\\"},{\\\"kind\\\":\\\"TEXT\\\",\\\"value\\\":\\\"b\\\"},{\\\"kind\\\":\\\"TEXT\\\",\\\"value\\\":\\\"c\\\"}]\"}"
    val encoded = URLEncoder.encode(json, StandardCharsets.UTF_8.toString())

    val rule = decodeElementPickerDomRuleResult("\"$encoded\"")

    assertEquals("[role=\"tab\"]", rule?.itemSelector)
    assertEquals(fingerprints, rule?.targetValue)
    assertFalse(rule?.itemSelector.orEmpty().contains("aria-selected"))
  }
}
