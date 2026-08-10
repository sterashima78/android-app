plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.bookmark.data"
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
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":core:database"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("org.jsoup:jsoup:1.22.2")

  testImplementation("junit:junit:4.13.2")
}
