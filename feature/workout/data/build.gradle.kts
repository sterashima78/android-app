plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.workout.data"
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
  implementation(project(":feature:workout:domain"))
}
