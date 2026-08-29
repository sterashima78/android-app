package dev.terashima.yomitorirss.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class MainActivityFeatureBoundaryDetectorTest : LintDetectorTest() {
  override fun getDetector(): Detector = MainActivityFeatureBoundaryDetector()

  override fun getIssues(): MutableList<Issue> = mutableListOf(MainActivityFeatureBoundaryDetector.ISSUE)

  override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

  fun testRejectsFeatureViewModelImport() {
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

  fun testRejectsAliasedFeatureViewModelImport() {
    lint()
      .files(
        kotlin(
          """
          package dev.terashima.yomitorirss
          import dev.terashima.yomitorirss.feature.rss.RssViewModel  as  FeedModel
          class MainActivity
          """.trimIndent(),
        ).to("src/dev/terashima/yomitorirss/MainActivity.kt"),
      )
      .run()
      .expectContains("[MainActivityFeatureBoundary]")
  }

  fun testRejectsConcreteFeatureDataImport() {
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

  fun testAllowsAppShellViewModelImport() {
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

  fun testIgnoresOtherActivity() {
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
