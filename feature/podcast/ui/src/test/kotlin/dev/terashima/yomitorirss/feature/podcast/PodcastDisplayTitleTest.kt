package dev.terashima.yomitorirss.feature.podcast

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastDisplayTitleTest {
  @Test
  fun `ニュース間の接続語はタイトル表示から除外する`() {
    assertEquals("次のニュース", podcastDisplayTitle("続いて。次のニュース"))
  }

  @Test
  fun `通常のタイトルはそのまま表示する`() {
    assertEquals("最初のニュース", podcastDisplayTitle("最初のニュース"))
  }
}
