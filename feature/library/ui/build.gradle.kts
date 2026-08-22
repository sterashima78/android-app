plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.library.ui"
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
}

dependencies {
  implementation(project(":core:web-collector"))
  implementation(project(":feature:library:domain"))

  implementation(platform("androidx.compose:compose-bom:2026.06.00"))
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.material3:material3")
  implementation("io.coil-kt.coil3:coil-compose:3.5.0")
  implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  testImplementation("junit:junit:4.13.2")
}
