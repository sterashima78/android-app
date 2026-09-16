plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.task.data"
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
  implementation(project(":core:database"))
  implementation(project(":feature:task:domain"))
}
