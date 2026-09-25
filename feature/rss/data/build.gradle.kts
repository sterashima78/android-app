plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.rss.data"
  compileSdk = 37

  defaultConfig {
    minSdk = 35
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":core:ai-inference"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":core:network"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:rss:domain"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation(libs.jsoup)
  implementation(libs.kotlinx.serialization.json)

  testImplementation("junit:junit:4.13.2")
  testImplementation("androidx.test:core-ktx:1.7.0")
  testImplementation("org.robolectric:robolectric:4.17")
  testImplementation("org.json:json:20260719")
}
