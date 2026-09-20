plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.video.data"
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
  implementation(project(":feature:video:domain"))
  implementation(project(":feature:library:domain"))
  implementation(project(":core:database"))
  implementation(project(":core:network"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.media3.effect)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.inspector)
  implementation(libs.androidx.media3.inspector.frame)
  implementation(libs.androidx.webkit)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.jsoup)
  testImplementation(libs.junit4)
  testImplementation("androidx.test:core-ktx:1.7.0")
  testImplementation("org.robolectric:robolectric:4.17")
}
