plugins {
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":feature:ai-task-queue:domain"))
  implementation(project(":feature:summary:domain"))
  implementation(project(":feature:library:domain"))
  implementation(project(":feature:knowledge:domain"))

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
