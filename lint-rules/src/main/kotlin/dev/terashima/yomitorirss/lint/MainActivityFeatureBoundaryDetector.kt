package dev.terashima.yomitorirss.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

class MainActivityFeatureBoundaryDetector : Detector(), SourceCodeScanner {
  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UImportStatement::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitImportStatement(node: UImportStatement) {
        if (context.file.name != MAIN_ACTIVITY_FILE_NAME) return

        val importText = node.sourcePsi?.text ?: return
        val importedName = importText
          .removePrefix("import")
          .trim()
          .substringBefore(" as ")
          .trim()

        when {
          importedName.startsWith(FEATURE_PACKAGE_PREFIX) && importedName.endsWith("ViewModel") -> {
            context.report(
              issue = ISSUE,
              scope = node,
              location = context.getLocation(node),
              message = "MainActivity must not import feature-owned ViewModels; use injected app/framework contracts.",
            )
          }

          importedName.startsWith(FEATURE_PACKAGE_PREFIX) && ".data." in importedName -> {
            context.report(
              issue = ISSUE,
              scope = node,
              location = context.getLocation(node),
              message = "MainActivity must not import concrete feature data; use injected contracts.",
            )
          }
        }
      }
    }

  companion object {
    private const val MAIN_ACTIVITY_FILE_NAME = "MainActivity.kt"
    private const val FEATURE_PACKAGE_PREFIX = "dev.terashima.yomitorirss.feature."

    val ISSUE: Issue = Issue.create(
      id = "MainActivityFeatureBoundary",
      briefDescription = "MainActivity feature boundary violation",
      explanation = """
        MainActivity is the framework-created executable entry point. Feature ViewModels and concrete
        feature Data implementations must stay behind the application composition/presentation
        boundaries and be supplied through narrow injected contracts.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(
        MainActivityFeatureBoundaryDetector::class.java,
        Scope.JAVA_FILE_SCOPE,
      ),
    )
  }
}
