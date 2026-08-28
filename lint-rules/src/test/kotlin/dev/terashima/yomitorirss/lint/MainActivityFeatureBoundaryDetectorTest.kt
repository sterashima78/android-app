package dev.terashima.yomitorirss.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class MainActivityFeatureBoundaryDetectorTest : LintDetectorTest() {
  override fun getDetector(): Detector = MainActivityFeatureBoundaryDetector()

  override fun getIssues(): MutableList<Issue> = mutableListOf(MainActivityFeatureBoundaryDetector.ISSUE)

  @Test
  fun `feature ViewModel importを拒否する`() {
    lint()
      .files(
        kotlin(
          """
          package dev.terashima.yomitorirss
          import dev.terashima.yomitorirss.feature.rss.RssViewModel
          class MainActivity
          """.trimIndent(),
        ).to("src/dev/terashima/yomitorirss/MainActivity.kt"),
      )
      .run()
      .expectContains("[MainActivityFeatureBoundary]")
  }

  @Test
  fun `aliased feature ViewModel importを拒否する`() {
    lint()
      .files(
        kotlin(
          """
          package dev.terashima.yomitorirss
          import dev.terashima.yomitorirss.feature.rss.RssViewModel as FeedModel
          class MainActivity
          """.trimIndent(),
        ).to("src/dev/terashima/yomitorirss/MainActivity.kt"),
      )
      .run()
      .expectContains("[MainActivityFeatureBoundary]")
  }

  @Test
  fun `concrete feature Data importを拒否する`() {
    lint()
      .files(
        kotlin(
          """
          package dev.terashima.yomitorirss
          import dev.terashima.yomitorirss.feature.web.data.LanWebServerService
          class MainActivity
          """.trimIndent(),
        ).to("src/dev/terashima/yomitorirss/MainActivity.kt"),
      )
      .run()
      .expectContains("[MainActivityFeatureBoundary]")
  }

  @Test
  fun `app shell ViewModel importは許可する`() {
    lint()
      .files(
        kotlin(
          """
          package dev.terashima.yomitorirss
          import dev.terashima.yomitorirss.ui.AppViewModel
          class MainActivity
          """.trimIndent(),
        ).to("src/dev/terashima/yomitorirss/MainActivity.kt"),
      )
      .run()
      .expectClean()
  }

  @Test
  fun `MainActivity以外ではこのentrypoint ruleを適用しない`() {
    lint()
      .files(
        kotlin(
          """
          package dev.terashima.yomitorirss
          import dev.terashima.yomitorirss.feature.rss.RssViewModel
          class OtherActivity
          """.trimIndent(),
        ).to("src/dev/terashima/yomitorirss/OtherActivity.kt"),
      )
      .run()
      .expectClean()
  }
}
