plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.reddit.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 35
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":feature:reddit:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:rss:domain"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
