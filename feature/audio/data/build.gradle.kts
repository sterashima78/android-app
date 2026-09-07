plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.audio.data"
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
  implementation(project(":feature:audio:domain"))
  implementation(project(":feature:summary:domain"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit4)
}
