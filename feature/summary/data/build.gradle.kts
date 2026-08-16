plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.summary.data"
  compileSdk = 37

  defaultConfig {
    minSdk = 29
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":feature:summary:domain"))
  implementation(project(":feature:article:data"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":core:ai-runtime"))

  implementation("androidx.core:core-ktx:1.18.0")
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  testImplementation("junit:junit:4.13.2")
  testImplementation("androidx.test:core-ktx:1.7.0")
  testImplementation("org.robolectric:robolectric:4.16.1")
}
