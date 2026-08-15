package dev.terashima.yomitorirss.feature.youtube

fun isYouTubeVideoUrl(input: String): Boolean = YOUTUBE_VIDEO_URL_REGEX.containsMatchIn(input)

private val YOUTUBE_VIDEO_URL_REGEX = Regex(
  pattern = "(?:youtube\\.com/watch\\?(?:[^#]*&)?v=|youtu\\.be/|youtube\\.com/shorts/)([A-Za-z0-9_-]{11})",
  option = RegexOption.IGNORE_CASE,
)
