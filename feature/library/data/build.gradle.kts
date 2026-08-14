plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.library.data"
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
  implementation(project(":feature:library:domain"))
  implementation(project(":core:database"))
  implementation(project(":core:network"))

  implementation("com.google.android.gms:play-services-auth:21.5.0")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20260522")
}
