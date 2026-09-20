plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.bookmark.data"
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
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":core:database"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation(libs.jsoup)

  testImplementation("junit:junit:4.13.2")
  testImplementation("androidx.test:core-ktx:1.7.0")
  testImplementation("org.robolectric:robolectric:4.17")
}
