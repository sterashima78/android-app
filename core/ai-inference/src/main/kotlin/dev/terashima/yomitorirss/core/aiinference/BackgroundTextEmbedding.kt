package dev.terashima.yomitorirss.core.aiinference

/** Provider-neutral on-device document embedding for offline grouping. */
interface BackgroundTextEmbedding {
  /** Embeds the inputs in order; callers validate shape and finite values. */
  suspend fun embed(texts: List<String>): List<FloatArray>
}
