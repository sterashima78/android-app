plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.library.data"
  compileSdk = 36

  defaultConfig {
    minSdk = 35
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
  implementation(project(":feature:library:domain"))
  implementation(project(":core:ai-inference"))
  implementation(project(":core:ai-runtime"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":core:network"))

  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.webkit:webkit:1.16.0")
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("com.google.android.gms:play-services-auth:21.6.0")
  implementation("com.hierynomus:smbj:0.14.0")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20260719")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("androidx.test:core-ktx:1.7.0")
}
