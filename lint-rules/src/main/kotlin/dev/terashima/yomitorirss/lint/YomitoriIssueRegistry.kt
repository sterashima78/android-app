package dev.terashima.yomitorirss.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API

class YomitoriIssueRegistry : IssueRegistry() {
  override val issues = listOf(MainActivityFeatureBoundaryDetector.ISSUE)
  override val api = CURRENT_API
  override val minApi = CURRENT_API
}
