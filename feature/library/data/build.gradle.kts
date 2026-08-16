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
  implementation(project(":core:ai-runtime"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":core:network"))

  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation("com.google.android.gms:play-services-auth:21.5.0")
  implementation("com.hierynomus:smbj:0.14.0")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20260522")
}
