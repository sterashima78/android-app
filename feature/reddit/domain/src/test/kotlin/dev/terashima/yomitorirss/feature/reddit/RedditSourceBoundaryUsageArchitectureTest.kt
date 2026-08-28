package dev.terashima.yomitorirss.feature.reddit

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditSourceBoundaryUsageArchitectureTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "feature").isDirectory }
      ?: error("repository root not found")
  }

  @Test
  fun `App route compositionはRedditの低レベル分類規則を再実装しない`() {
    val routeComposition = source(
      "app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt",
    )

    assertTrue("route composition must consume the Reddit-owned boundary", "RedditSourceBoundary" in routeComposition)
    listOf(
      "isRedditArticle",
      "isRedditFeedUrl",
      "redditCommunityFeedUrl",
      "redditThreadId",
    ).forEach { lowLevelRule ->
      assertFalse(
        "route composition must not depend on Reddit low-level rule: $lowLevelRule",
        "feature.reddit.$lowLevelRule" in routeComposition,
      )
    }
  }

  @Test
  fun `Redditの低レベル分類APIはowner feature外へ公開利用しない`() {
    val forbiddenImports = listOf(
      "import dev.terashima.yomitorirss.feature.reddit.isRedditArticle",
      "import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl",
      "import dev.terashima.yomitorirss.feature.reddit.redditCommunityFeedUrl",
      "import dev.terashima.yomitorirss.feature.reddit.redditThreadId",
    )

    repositoryRoot.walkTopDown()
      .filter(File::isFile)
      .filter { it.extension == "kt" }
      .filter { "/src/main/" in it.invariantSeparatorsPath }
      .filterNot { "/feature/reddit/" in it.invariantSeparatorsPath }
      .forEach { file ->
        val fileSource = file.readText()
        forbiddenImports.forEach { forbiddenImport ->
          assertFalse(
            "${file.relativeTo(repositoryRoot)} must consume RedditSourceBoundary instead of $forbiddenImport",
            forbiddenImport in fileSource,
          )
        }
      }
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
