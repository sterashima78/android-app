plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.rss.ui"
  compileSdk = 36

  defaultConfig {
    minSdk = 29
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
  implementation(project(":feature:backup:domain"))
  implementation(project(":feature:rss:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:article:ui"))
  implementation(project(":feature:bookmark:domain"))

  implementation(platform("androidx.compose:compose-bom:2026.06.00"))
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
