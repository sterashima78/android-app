package dev.terashima.yomitorirss.feature.chat.data

import dev.terashima.yomitorirss.feature.chat.ChatContextBlock
import dev.terashima.yomitorirss.feature.chat.ChatRole
import dev.terashima.yomitorirss.feature.chat.ChatTurn

internal object ChatPrompt {
  private const val SYSTEM_PROMPT =
    "あなたはYomitoriアプリ内のAIアシスタントです。日本語で簡潔かつ正確に回答してください。" +
      "アプリ内データについては、参照情報として明示的に渡された内容だけを根拠にし、見えていないデータへアクセスできるとは主張しないでください。"

  fun render(
    turns: List<ChatTurn>,
    context: List<ChatContextBlock>,
    maxInputChars: Int,
    chatMl: Boolean,
  ): String {
    val normalizedContext = context
      .mapNotNull { block ->
        val value = block.content.trim()
        if (value.isBlank()) null else "[${block.label}]\n$value"
      }
      .joinToString("\n\n")
      .take((maxInputChars / 2).coerceAtLeast(256))

    val contextSection = if (normalizedContext.isBlank()) {
      ""
    } else {
      "\n\n参照情報:\n$normalizedContext"
    }
    val fixedLength = SYSTEM_PROMPT.length + contextSection.length + 160
    val conversationBudget = (maxInputChars - fixedLength).coerceAtLeast(256)
    val keptTurns = trimTurns(turns, conversationBudget)

    return if (chatMl) {
      buildString {
        append("<|im_start|>system\n")
        append(SYSTEM_PROMPT)
        append(contextSection)
        append("<|im_end|>\n")
        keptTurns.forEach { turn ->
          append("<|im_start|>")
          append(if (turn.role == ChatRole.USER) "user" else "assistant")
          append('\n')
          append(turn.content)
          append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
      }
    } else {
      buildString {
        append(SYSTEM_PROMPT)
        append(contextSection)
        append("\n\n会話履歴:\n")
        keptTurns.forEach { turn ->
          append(if (turn.role == ChatRole.USER) "ユーザー: " else "アシスタント: ")
          append(turn.content)
          append('\n')
        }
        append("アシスタント: ")
      }
    }
  }

  internal fun trimTurns(turns: List<ChatTurn>, maxChars: Int): List<ChatTurn> {
    val normalized = turns.mapNotNull { turn ->
      val content = turn.content.trim()
      if (content.isBlank()) null else turn.copy(content = content)
    }
    if (normalized.isEmpty()) return emptyList()

    var remaining = maxChars.coerceAtLeast(1)
    val result = mutableListOf<ChatTurn>()
    for (turn in normalized.asReversed()) {
      val overhead = 20
      val available = (remaining - overhead).coerceAtLeast(0)
      if (available <= 0) break
      if (turn.content.length <= available) {
        result += turn
        remaining -= turn.content.length + overhead
      } else if (result.isEmpty()) {
        result += turn.copy(content = turn.content.take(available))
        remaining = 0
      } else {
        break
      }
    }
    return result.asReversed()
  }
}
