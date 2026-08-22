plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.core.database"
  compileSdk = 36

  defaultConfig {
    minSdk = 34
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

  testImplementation("junit:junit:4.13.2")
}
