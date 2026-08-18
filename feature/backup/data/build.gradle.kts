plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.backup.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 29
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
}

dependencies {
  implementation(project(":feature:backup:domain"))
  implementation(project(":feature:bookmark:data"))
  implementation(project(":core:database"))

  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("androidx.test:core-ktx:1.7.0")
}
