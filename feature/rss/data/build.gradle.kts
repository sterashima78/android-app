plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.rss.data"
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
  implementation(project(":core:database"))
  implementation(project(":core:network"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:rss:domain"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation("org.jsoup:jsoup:1.23.1")

  testImplementation("junit:junit:4.13.2")
}
