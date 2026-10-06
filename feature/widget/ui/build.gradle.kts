plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.widget.ui"
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
  implementation(project(":feature:widget:domain"))
  implementation(project(":feature:task:domain"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  testImplementation(libs.junit4)
  testImplementation("org.robolectric:robolectric:4.17")
}
