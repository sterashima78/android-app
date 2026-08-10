plugins {
  id("com.android.application") version "9.3.0" apply false
  id("com.android.library") version "9.3.0" apply false
  id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

subprojects {
  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    dependencies.add("testImplementation", "junit:junit:4.13.2")
  }
  pluginManager.withPlugin("com.android.library") {
    dependencies.add("testImplementation", "junit:junit:4.13.2")
  }
  pluginManager.withPlugin("com.android.application") {
    dependencies.add("testImplementation", "junit:junit:4.13.2")
  }
}
