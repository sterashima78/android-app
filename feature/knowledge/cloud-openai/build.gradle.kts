plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.knowledge.cloudopenai"
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
  implementation(project(":feature:knowledge:domain"))
  implementation(project(":core:ai-inference"))
  implementation(project(":core:ai-cloud-openai"))
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit4)
}
