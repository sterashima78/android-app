plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.settings.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 34
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
  implementation(libs.kotlinx.coroutines.android)
}
