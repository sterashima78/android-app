package dev.terashima.yomitorirss.core.airuntime

internal object ThinkingMode {
  fun apply(prompt: String, enabled: Boolean): String =
    "${if (enabled) "/think" else "/no_think"}\n$prompt"

  fun cacheVariant(enabled: Boolean): String = if (enabled) "think" else "no_think"
}
