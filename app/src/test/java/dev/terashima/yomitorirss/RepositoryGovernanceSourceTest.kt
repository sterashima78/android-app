package dev.terashima.yomitorirss

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryGovernanceSourceTest {
  private val repositoryRoot: File by lazy {
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }
      ?: error("repository root not found")
  }

  private val compositionSourceRoot = "app/composition/src/main/java/dev/terashima/yomitorirss"

  @Test
  fun `診断artifactはgit管理対象外にする`() {
    val gitignore = source(".gitignore")
    listOf(
      "*.hprof",
      "*.trace",
      "*.perfetto-trace",
      "*.perfetto-trace-unredacted",
      "*.heapprofile",
      "*.heapdump",
      "*.heapsnapshot",
    ).forEach { pattern ->
      assertTrue(".gitignore must exclude diagnostic artifact: $pattern", pattern in gitignore)
    }
  }

  @Test
  fun `共有dependency versionはcatalogを正本にする`() {
    val catalog = source("gradle/libs.versions.toml")
    val settingsData = source("feature/settings/data/build.gradle.kts")
    val chatData = source("feature/chat/data/build.gradle.kts")

    assertTrue("catalog must own the AndroidX Core version", "androidx-core = \"1.17.0\"" in catalog)
    assertTrue("catalog must own the AndroidX Activity version", "androidx-activity = \"1.13.0\"" in catalog)
    assertTrue("catalog must own the AndroidX Navigation version", "androidx-navigation = \"2.9.8\"" in catalog)
    assertTrue("catalog must own the AndroidX WebKit version", "androidx-webkit = \"1.16.0\"" in catalog)
    assertTrue("catalog must own the coroutines version", "kotlinx-coroutines = \"1.11.0\"" in catalog)
    assertTrue("catalog must own the serialization version", "kotlinx-serialization = \"1.11.0\"" in catalog)
    assertTrue("catalog must own the JUnit4 version", "junit4 = \"4.13.2\"" in catalog)

    val expectedAliases = mapOf(
      "app/build.gradle.kts" to listOf(
        "libs.androidx.core.ktx",
        "libs.androidx.activity.compose",
        "libs.androidx.navigation.compose",
      ),
      "app/presentation/build.gradle.kts" to listOf(
        "libs.androidx.core.ktx",
        "libs.androidx.activity.compose",
        "libs.androidx.navigation.compose",
      ),
      "feature/web/data/build.gradle.kts" to listOf("libs.androidx.core.ktx"),
      "feature/summary/data/build.gradle.kts" to listOf("libs.androidx.core.ktx"),
      "feature/knowledge/data/build.gradle.kts" to listOf("libs.androidx.core.ktx"),
      "feature/library/data/build.gradle.kts" to listOf(
        "libs.androidx.core.ktx",
        "libs.androidx.webkit",
      ),
      "feature/asset/ui/build.gradle.kts" to listOf("libs.androidx.activity.compose"),
      "feature/health/ui/build.gradle.kts" to listOf("libs.androidx.activity.compose"),
      "feature/workout/ui/build.gradle.kts" to listOf("libs.androidx.activity.compose"),
      "feature/library/ui/build.gradle.kts" to listOf("libs.androidx.activity.compose"),
      "feature/bookmark/ui/build.gradle.kts" to listOf("libs.androidx.activity.compose"),
    )
    expectedAliases.forEach { (path, aliases) ->
      val buildScript = source(path)
      aliases.forEach { alias ->
        assertTrue("$path must use catalog alias $alias", alias in buildScript)
      }
    }

    val migratedCoordinates = listOf(
      "androidx.core:core-ktx:",
      "androidx.activity:activity-compose:",
      "androidx.navigation:navigation-compose:",
      "androidx.webkit:webkit:",
    )
    val buildScripts = repositoryRoot.walkTopDown()
      .onEnter { directory -> directory.name !in setOf(".git", ".gradle", "build") }
      .filter { it.isFile && it.name == "build.gradle.kts" }
      .toList()
    migratedCoordinates.forEach { coordinate ->
      val offenders = buildScripts
        .filter { coordinate in it.readText() }
        .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
        .sorted()
      assertTrue(
        "migrated dependency $coordinate must not be hardcoded in build scripts: $offenders",
        offenders.isEmpty(),
      )
    }

    assertTrue("Settings data must use the coroutines catalog alias", "libs.kotlinx.coroutines.android" in settingsData)
    assertFalse("Settings data must not hardcode the coroutines version", "kotlinx-coroutines-android:1.11.0" in settingsData)

    assertTrue("Chat data must use the coroutines catalog alias", "libs.kotlinx.coroutines.android" in chatData)
    assertTrue("Chat data must use the serialization catalog alias", "libs.kotlinx.serialization.json" in chatData)
    assertTrue("Chat data must use the JUnit catalog alias", "libs.junit4" in chatData)
    assertFalse("Chat data must not hardcode migrated dependency versions", ":1.11.0\"" in chatData)
    assertFalse("Chat data must not hardcode JUnit4 version", "junit:junit:4.13.2" in chatData)
  }

  @Test
  fun `Gradle wrapper versionは検証済みbaselineを使う`() {
    val wrapper = source("gradle/wrapper/gradle-wrapper.properties")
    assertTrue("Gradle wrapper must use 9.6.1", "gradle-9.6.1-bin.zip" in wrapper)
  }

  @Test
  fun `PR quality checksとmain signed APK buildはworkflowを分離する`() {
    val checkWorkflow = source(".github/workflows/check.yml")
    val buildWorkflow = source(".github/workflows/build.yml")

    listOf("Public repository", "Architecture", "Test", "Lint").forEach { checkName ->
      assertTrue("PR workflow must expose the required check: $checkName", "name: $checkName" in checkWorkflow)
    }
    assertTrue(
      "Architecture check must include Gradle architecture metadata verification",
      "-I gradle/architecture-metadata.gradle.kts" in checkWorkflow,
    )
    assertFalse(
      "Architecture check must not invoke the retired Python ADR verifier",
      "python3 scripts/verify_adr_integrity.py" in checkWorkflow,
    )
    assertFalse("PR check workflow must not assemble the release APK", ":app:assembleRelease" in checkWorkflow)
    assertTrue("main build workflow must assemble the signed release APK", ":app:assembleRelease" in buildWorkflow)
    assertFalse("main build workflow must not rerun the PR quality matrix", "matrix:" in buildWorkflow)
  }

  @Test
  fun `external version identifiersはBuildConfig versionを正本にする`() {
    val application = source("app/src/main/java/dev/terashima/yomitorirss/YomitoriApplication.kt")
    val container = source("$compositionSourceRoot/AppContainer.kt")
    val network = source(
      "core/network/src/main/kotlin/dev/terashima/yomitorirss/core/network/OkHttpHttpClient.kt",
    )
    val workflow = source(".github/workflows/build.yml")

    assertTrue(
      "application must pass BuildConfig version across the composition boundary",
      "appVersionName = BuildConfig.VERSION_NAME" in application,
    )
    assertTrue(
      "application User-Agent must derive the version from the injected app version",
      "Mosaic/${'$'}appVersionName (Android)" in container,
    )
    assertFalse("composition module must not depend on app BuildConfig", "BuildConfig.VERSION_NAME" in container)
    assertFalse("core network must not hardcode the app version", "Mosaic/0.2" in network)
    assertFalse("APK workflow must not hardcode the app version", "mosaic-0.2.0-arm64-v8a.apk" in workflow)
    assertTrue("APK workflow must read output metadata versionName", "[\"versionName\"]" in workflow)
  }

  private fun source(path: String): String = File(repositoryRoot, path).readText()
}
