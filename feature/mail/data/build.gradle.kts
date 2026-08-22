plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.mail.data"
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
  implementation(project(":feature:mail:domain"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":core:network"))

  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("com.google.android.gms:play-services-auth:21.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  testImplementation("junit:junit:4.13.2")
}
