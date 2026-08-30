plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "dev.terashima.yomitorirss.feature.integrated.ui"
  compileSdk = 36

  defaultConfig {
    minSdk = 35
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:mail:domain"))
  implementation(project(":feature:mail:ui"))
  implementation(project(":feature:reddit:domain"))
  implementation(project(":feature:reddit:ui"))
  implementation(project(":feature:rss:ui"))
  implementation(project(":feature:youtube:domain"))
  implementation(project(":feature:youtube:ui"))
  implementation(project(":core:designsystem"))

  implementation(platform("androidx.compose:compose-bom:2026.06.00"))
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")

  testImplementation("junit:junit:4.13.2")
}
