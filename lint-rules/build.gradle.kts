plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  compileOnly("com.android.tools.lint:lint-api:32.3.0")

  testImplementation("com.android.tools.lint:lint-tests:32.3.0")
  testImplementation(libs.junit4)
}
