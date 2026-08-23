plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.widget.ui"
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
  implementation(project(":core:background"))
  implementation(project(":feature:widget:domain"))
  implementation(project(":feature:task:domain"))
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
}
