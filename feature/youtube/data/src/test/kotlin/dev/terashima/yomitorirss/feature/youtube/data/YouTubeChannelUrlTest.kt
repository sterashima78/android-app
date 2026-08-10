package dev.terashima.yomitorirss.feature.youtube.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YouTubeChannelUrlTest {
  @Test
  fun `channel URLからchannel idを取得できる`() {
    assertEquals(
      "UC_x5XG1OV2P6uZZ5FSM9Ttw",
      YouTubeChannelUrl.channelId("https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw"),
    )
  }

  @Test
  fun `末尾スラッシュ付きchannel URLを受け付ける`() {
    assertEquals(
      "UC_x5XG1OV2P6uZZ5FSM9Ttw",
      YouTubeChannelUrl.channelId("https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw/"),
    )
  }

  @Test
  fun `handle URLは受け付けない`() {
    assertThrows(IllegalArgumentException::class.java) {
      YouTubeChannelUrl.channelId("https://www.youtube.com/@GoogleDevelopers")
    }
  }

  @Test
  fun `channel URLでもquery付きは受け付けない`() {
    assertThrows(IllegalArgumentException::class.java) {
      YouTubeChannelUrl.channelId(
        "https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw?sub_confirmation=1",
      )
    }
  }
}
