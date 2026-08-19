package dev.terashima.yomitorirss.feature.summary.data

private const val AUTO_FOLDER_NONE = "なし"

internal fun parseGeneratedFolder(
  raw: String,
  existingFolderNames: List<String>,
): String? {
  val candidate = raw
    .lineSequence()
    .map(String::trim)
    .firstOrNull(String::isNotBlank)
    .orEmpty()
    .removePrefix("-")
    .trim()
    .removePrefix("フォルダ:")
    .removePrefix("フォルダ：")
    .trim()
    .trim('"', '\'', '`')

  if (candidate.isBlank() || candidate.equals(AUTO_FOLDER_NONE, ignoreCase = true)) return null
  if (candidate.equals("none", ignoreCase = true) || candidate == "未分類") return null
  val normalizedCandidate = normalizeFolderName(candidate)
  return existingFolderNames.firstOrNull { normalizeFolderName(it) == normalizedCandidate }
}

private fun normalizeFolderName(name: String): String =
  name.trim().replace(Regex("\\s+"), " ").lowercase()
