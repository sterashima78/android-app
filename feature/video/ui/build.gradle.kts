plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.video.ui"
  compileSdk = 36

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
  implementation(project(":feature:video:domain"))
  implementation(project(":feature:library:domain"))

  implementation(platform("androidx.compose:compose-bom:2026.06.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-viewbinding")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.ui)
  implementation("io.coil-kt.coil3:coil-compose:3.5.0")
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit4)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
