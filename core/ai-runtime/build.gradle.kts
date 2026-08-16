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
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("com.google.ai.edge.litertlm:litertlm-android:0.14.0")
  implementation("org.bytedeco:sentencepiece:0.2.1-1.5.13")
  runtimeOnly("org.bytedeco:sentencepiece:0.2.1-1.5.13:android-arm64")
  runtimeOnly("org.bytedeco:javacpp:1.5.13:android-arm64")

  testImplementation("junit:junit:4.13.2")
}
