plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.chat.data"
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
  implementation(project(":feature:chat:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:reddit:domain"))
  implementation(project(":feature:rss:domain"))
  implementation(project(":feature:summary:domain"))
  implementation(project(":feature:task:domain"))
  implementation(project(":core:ai-runtime"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

  testImplementation("junit:junit:4.13.2")
}
