plugins {
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:reddit:domain"))
  implementation(project(":feature:youtube:domain"))

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  testImplementation("junit:junit:4.13.2")
}
