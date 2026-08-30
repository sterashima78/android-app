plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.web.data"
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
  implementation(project(":feature:web:domain"))
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  testImplementation("junit:junit:4.13.2")
}
