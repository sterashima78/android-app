plugins {
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:rss:domain"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
