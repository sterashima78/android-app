plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.chat.data"
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
  implementation(project(":core:database"))
  implementation(project(":feature:chat:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:reddit:domain"))
  implementation(project(":feature:rss:domain"))
  implementation(project(":feature:summary:domain"))
  implementation(project(":feature:task:domain"))
  implementation(project(":core:ai-runtime"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit4)
}
