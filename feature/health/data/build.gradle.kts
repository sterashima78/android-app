plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.health.data"
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
  implementation(project(":feature:health:domain"))
  implementation("androidx.health.connect:connect-client:1.1.0")

  testImplementation("junit:junit:4.13.2")
}
