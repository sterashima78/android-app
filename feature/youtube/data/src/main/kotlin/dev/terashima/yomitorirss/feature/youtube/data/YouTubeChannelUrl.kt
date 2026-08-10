package dev.terashima.yomitorirss.feature.youtube.data

import java.net.URI

internal object YouTubeChannelUrl {
  private val channelIdPattern = Regex("^UC[A-Za-z0-9_-]{22}$")

  fun channelId(input: String): String {
    val uri = runCatching { URI(input.trim()) }
      .getOrElse { throw IllegalArgumentException("YouTubeチャンネルURLが正しくありません") }
    val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
    require(
      uri.scheme == "https" &&
        uri.host == "www.youtube.com" &&
        uri.rawQuery == null &&
        uri.rawFragment == null &&
        segments.size == 2 &&
        segments[0] == "channel" &&
        channelIdPattern.matches(segments[1]),
    ) {
      "https://www.youtube.com/channel/UC... 形式のURLを入力してください"
    }
    return segments[1]
  }

  fun canonical(channelId: String): String = "https://www.youtube.com/channel/$channelId"

  fun feed(channelId: String): String =
    "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
}
