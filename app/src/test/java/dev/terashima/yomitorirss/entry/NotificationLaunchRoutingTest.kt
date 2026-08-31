package dev.terashima.yomitorirss.entry

import dev.terashima.yomitorirss.composition.background.IntegratedRefreshNotificationContract
import dev.terashima.yomitorirss.feature.web.LanWebServerLaunchContract
import dev.terashima.yomitorirss.ui.AppNavigationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationLaunchRoutingTest {
  @Test
  fun `新着通知は統合ビュー要求へ解決する`() {
    assertEquals(
      AppNavigationTarget.INTEGRATED,
      notificationLaunchTarget(IntegratedRefreshNotificationContract.ACTION_OPEN_INTEGRATED),
    )
  }

  @Test
  fun `Webサーバ通知はWebサーバ管理要求へ解決する`() {
    assertEquals(
      AppNavigationTarget.WEB_SERVER,
      notificationLaunchTarget(LanWebServerLaunchContract.ACTION_OPEN_SERVER),
    )
  }

  @Test
  fun `無関係な通知アクションは画面要求へ解決しない`() {
    assertNull(notificationLaunchTarget("dev.terashima.yomitorirss.action.OTHER"))
    assertNull(notificationLaunchTarget(null))
  }
}
