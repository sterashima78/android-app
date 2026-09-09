plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.podcast.data"
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
  implementation(project(":core:ai-inference"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":feature:podcast:domain"))
  implementation(project(":feature:rss:domain"))
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  testImplementation(libs.junit4)
}
