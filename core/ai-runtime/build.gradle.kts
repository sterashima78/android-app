plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.core.airuntime"
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
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation("com.google.mediapipe:tasks-genai:0.10.35")
  implementation("com.google.ai.edge.litertlm:litertlm-android:0.14.0")
}
