plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.podcast.data"
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
  implementation(project(":core:ai-cloud-openai"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":feature:podcast:domain"))
  implementation(project(":feature:rss:domain"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.serialization.json)
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  testImplementation(libs.junit4)
  testImplementation("androidx.test:core-ktx:1.7.0")
  testImplementation("org.robolectric:robolectric:4.17")
}
