plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.workout.data"
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
  implementation(project(":feature:workout:domain"))
  implementation("androidx.health.connect:connect-client:1.1.0")

  testImplementation(libs.junit4)
  testImplementation("androidx.test:core-ktx:1.7.0")
  testImplementation("org.robolectric:robolectric:4.17")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
