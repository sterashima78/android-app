package dev.terashima.yomitorirss.core.airuntime

/** Current Gemma 4 LiteRT-LM artifacts in the app expose a 32K execution context. */
internal val LocalModelStatus.maxContextTokens: Int
  get() = when (id) {
    "gemma4-e2b-it", "gemma4-e4b-it" -> 32_768
    else -> contextTokens
  }
