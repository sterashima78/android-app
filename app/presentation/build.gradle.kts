import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.terashima.yomitorirss.presentation"
  compileSdk = 36

  defaultConfig {
    minSdk = 34
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  lint {
    abortOnError = true
    checkReleaseBuilds = true
  }
}

val verifyPresentationBoundary by tasks.registering {
  group = "verification"
  description = "Verifies that app presentation depends on contracts/UI only, never executable app or feature data."

  doLast {
    val forbidden = configurations
      .filterNot { "test" in it.name.lowercase() }
      .flatMap { configuration ->
        configuration.dependencies
          .withType(ProjectDependency::class.java)
          .map { dependency -> configuration.name to dependency.path }
      }
      .filter { (_, target) -> target == ":app" || (target.startsWith(":feature:") && target.endsWith(":data")) }
      .distinct()
      .sortedBy { (configuration, target) -> "$configuration:$target" }

    if (forbidden.isNotEmpty()) {
      throw GradleException(
        buildString {
          appendLine(":app:presentation must not depend on executable :app or feature data modules:")
          forbidden.forEach { (configuration, target) -> appendLine("- $configuration -> $target") }
          append("Use :app:composition and feature Domain/UI contracts across the presentation boundary.")
        },
      )
    }
  }
}

rootProject.tasks.named("verifyArchitecture").configure {
  dependsOn(verifyPresentationBoundary)
}

dependencies {
  implementation(project(":app:composition"))

  implementation(project(":feature:ai-task-queue:domain"))
  implementation(project(":feature:ai-task-queue:ui"))
  implementation(project(":feature:backup:domain"))
  implementation(project(":feature:backup:ui"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:bookmark:ui"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:article:ui"))
  implementation(project(":feature:asset:domain"))
  implementation(project(":feature:asset:ui"))
  implementation(project(":feature:book-reader:domain"))
  implementation(project(":feature:book-reader:ui"))
  implementation(project(":feature:chat:domain"))
  implementation(project(":feature:chat:ui"))
  implementation(project(":feature:calendar:domain"))
  implementation(project(":feature:calendar:ui"))
  implementation(project(":feature:game:domain"))
  implementation(project(":feature:game:ui"))
  implementation(project(":feature:health:domain"))
  implementation(project(":feature:health:ui"))
  implementation(project(":feature:integrated:ui"))
  implementation(project(":feature:library:domain"))
  implementation(project(":feature:library:ui"))
  implementation(project(":feature:knowledge:domain"))
  implementation(project(":feature:knowledge:ui"))
  implementation(project(":feature:mail:domain"))
  implementation(project(":feature:mail:ui"))
  implementation(project(":feature:reddit:domain"))
  implementation(project(":feature:reddit:ui"))
  implementation(project(":feature:rss:domain"))
  implementation(project(":feature:rss:ui"))
  implementation(project(":feature:summary:domain"))
  implementation(project(":feature:summary:ui"))
  implementation(project(":feature:settings:domain"))
  implementation(project(":feature:settings:ui"))
  implementation(project(":feature:task:domain"))
  implementation(project(":feature:task:ui"))
  implementation(project(":feature:web:domain"))
  implementation(project(":feature:web:ui"))
  implementation(project(":feature:widget:domain"))
  implementation(project(":feature:widget:ui"))
  implementation(project(":feature:workout:domain"))
  implementation(project(":feature:workout:ui"))
  implementation(project(":feature:youtube:domain"))
  implementation(project(":feature:youtube:ui"))
  implementation(project(":feature:x:domain"))
  implementation(project(":feature:x:ui"))

  implementation(platform("androidx.compose:compose-bom:2026.06.00"))
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation("androidx.navigation:navigation-compose:2.9.8")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation(libs.kotlinx.coroutines.android)

  debugImplementation("androidx.compose.ui:ui-tooling")
}
