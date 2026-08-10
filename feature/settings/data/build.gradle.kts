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
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
