plugins {
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  testImplementation(libs.junit4)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
