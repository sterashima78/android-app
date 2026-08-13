plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.settings.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 29
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":feature:settings:domain"))
  implementation(project(":core:ai-runtime"))
  implementation(project(":core:background"))
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
