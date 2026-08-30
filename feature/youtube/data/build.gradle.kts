plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.youtube.data"
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
  implementation(project(":core:database"))
  implementation(project(":feature:youtube:domain"))
  implementation(project(":core:network"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

  testImplementation("junit:junit:4.13.2")
}
