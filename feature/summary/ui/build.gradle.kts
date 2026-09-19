plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.summary.ui"
  compileSdk = 37

  defaultConfig {
    minSdk = 35
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":feature:summary:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":core:designsystem"))

  implementation(platform("androidx.compose:compose-bom:2026.09.00"))
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
