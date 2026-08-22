plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.x.data"
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
  implementation(project(":feature:x:domain"))

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("androidx.test:core-ktx:1.7.0")
}
