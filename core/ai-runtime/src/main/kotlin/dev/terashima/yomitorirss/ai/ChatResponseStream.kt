package dev.terashima.yomitorirss.core.airuntime
internal object ChatResponseStream {
  private const val ASSISTANT_PREFIX = "アシスタント"
  private val completeThinkBlock = Regex(
    "<think>.*?</think>",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
  )
  private val stopMarkers = listOf("<|im_end|>", "<end_of_turn>")
  private val controlMarkers = listOf("<think>") + stopMarkers

  fun partial(raw: String): String {
    var result = raw
    stopMarkers.forEach { marker ->
      val index = result.indexOf(marker, ignoreCase = true)
      if (index >= 0) result = result.substring(0, index)
    }
    result = result.replace(completeThinkBlock, "")

    val openThinkIndex = result.indexOf("<think>", ignoreCase = true)
    if (openThinkIndex >= 0) result = result.substring(0, openThinkIndex)

    result = stripAssistantPrefix(result)
    result = stripIncompleteControlMarker(result)
    return result.trimStart()
  }

  fun complete(raw: String): String {
    val result = partial(raw).trim()
    check(result.isNotBlank()) { "AIの応答が空です" }
    return result
  }

  private fun stripAssistantPrefix(value: String): String {
    val candidate = value.trimStart()
    if (
      candidate.length < ASSISTANT_PREFIX.length &&
      ASSISTANT_PREFIX.startsWith(candidate, ignoreCase = true)
    ) {
      return ""
    }
    if (!candidate.startsWith(ASSISTANT_PREFIX, ignoreCase = true)) return value

    var remainder = candidate.substring(ASSISTANT_PREFIX.length)
    if (remainder.startsWith(":") || remainder.startsWith("：")) {
      remainder = remainder.drop(1)
    }
    return remainder.trimStart()
  }

  private fun stripIncompleteControlMarker(value: String): String {
    for (index in value.indices.reversed()) {
      val suffix = value.substring(index)
      if (!suffix.startsWith("<")) continue
      if (controlMarkers.any { marker -> marker.startsWith(suffix, ignoreCase = true) }) {
        return value.substring(0, index)
      }
    }
    return value
  }
}
