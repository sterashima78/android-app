plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.core.aicloudopenai"
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
  implementation(project(":core:ai-inference"))
  implementation(project(":core:network"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit4)
}
