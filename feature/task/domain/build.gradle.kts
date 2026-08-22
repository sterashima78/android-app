plugins {
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
