package dev.terashima.yomitorirss

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = YomitoriApplication::class)
class StartupSmokeTest {
  @Test
  fun `MainActivity が起動して非同期初期化まで完了する`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val viewModels = ViewModelProvider(activity)
        val rssViewModel = viewModels[RssViewModel::class.java]
        val feedViewModel = viewModels[FeedViewModel::class.java]

        runBlocking {
          withTimeout(10_000) {
            rssViewModel.state.first { it.initialized }
            feedViewModel.state.first { it.initialized }
          }
        }

        assertFalse(activity.isFinishing)
      }
    }
  }
}
