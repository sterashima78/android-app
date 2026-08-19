plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.article.data"
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
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":core:database"))
  implementation(project(":core:network"))

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("org.jsoup:jsoup:1.22.2")

  testImplementation("junit:junit:4.13.2")
  testImplementation("androidx.test:core-ktx:1.7.0")
  testImplementation("org.robolectric:robolectric:4.16.1")
}
