plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.bookreader.data"
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
  implementation(project(":feature:book-reader:domain"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  testImplementation("junit:junit:4.13.2")
}
