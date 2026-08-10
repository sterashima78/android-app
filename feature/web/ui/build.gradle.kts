plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.web.ui"
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
  implementation(project(":feature:web:domain"))

  implementation(platform("androidx.compose:compose-bom:2026.06.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
}
