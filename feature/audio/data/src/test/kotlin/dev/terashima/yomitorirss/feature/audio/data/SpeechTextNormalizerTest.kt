package dev.terashima.yomitorirss.feature.audio.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextNormalizerTest {
  @Test
  fun `Markdownの表示記号を除いて読み上げ本文を残す`() {
    val markdown = """
      # 概要

      - **重要**: [公式ドキュメント](https://example.com)を確認する
      - `Kotlin` を利用する
      > 補足です

      1. 最初の項目
      2. 次の項目
    """.trimIndent()

    assertEquals(
      """
        概要

        重要: 公式ドキュメントを確認する
        Kotlin を利用する
        補足です

        最初の項目
        次の項目
      """.trimIndent(),
      markdownToSpeechText(markdown),
    )
  }

  @Test
  fun `表と取り消し線と参照リンクを読み上げ向けに整える`() {
    val markdown = """
      | 項目 | 内容 |
      | --- | --- |
      | 状態 | ~~旧仕様~~ 新仕様 |

      詳細は[仕様][spec]を参照。
    """.trimIndent()

    assertEquals(
      """
        項目、内容
        状態、旧仕様 新仕様

        詳細は仕様を参照。
      """.trimIndent(),
      markdownToSpeechText(markdown),
    )
  }

  @Test
  fun `引用元を示すURLリンクは読み上げない`() {
    val markdown = """
      要点です。([参照元](https://example.com/article))
      URL表記の参照です。[https://example.com/source](https://example.com/source)
      補足URL https://example.com/detail
      詳細は[仕様](https://example.com/spec)を参照。
    """.trimIndent()

    assertEquals(
      """
        要点です。
        URL表記の参照です。
        補足URL
        詳細は仕様を参照。
      """.trimIndent(),
      markdownToSpeechText(markdown),
    )
  }

  @Test
  fun `Markdownではない記号は保持する`() {
    assertEquals(
      "C# と snake_case_test はそのまま読む",
      markdownToSpeechText("C# と snake_case_test はそのまま読む"),
    )
  }
}
