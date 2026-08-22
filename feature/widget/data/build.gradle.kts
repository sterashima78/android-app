plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.widget.data"
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
  implementation(project(":feature:widget:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:rss:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:backup:domain"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
