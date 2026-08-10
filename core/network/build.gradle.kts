plugins {
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("com.squareup.okhttp3:okhttp:5.3.0")
}
