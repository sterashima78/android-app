plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.youtube.ui"
  compileSdk = 37

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
  implementation(project(":core:designsystem"))
  implementation(project(":feature:youtube:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:backup:domain"))

  implementation(platform("androidx.compose:compose-bom:2026.06.00"))
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("io.coil-kt.coil3:coil-compose:3.5.0")
  implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
