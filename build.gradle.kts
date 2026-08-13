import com.android.build.api.dsl.ApplicationExtension

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

    extensions.configure<ApplicationExtension> {
      defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }
      testOptions {
        animationsDisabled = true
        managedDevices {
          localDevices {
            create("pixel6Api35") {
              device = "Pixel 6"
              apiLevel = 35
              systemImageSource = "google"
              require64Bit = true
              testedAbi = "arm64-v8a"
            }
          }
        }
      }
    }

    dependencies.add("androidTestImplementation", dependencies.platform("androidx.compose:compose-bom:2026.06.00"))
    dependencies.add("androidTestImplementation", "androidx.compose.ui:ui-test-junit4")
    dependencies.add("androidTestImplementation", "androidx.test:core-ktx:1.7.0")
    dependencies.add("androidTestImplementation", "androidx.test:runner:1.7.0")
    dependencies.add("androidTestImplementation", "androidx.test.ext:junit:1.3.0")
    dependencies.add("androidTestImplementation", "androidx.test.uiautomator:uiautomator:2.4.0")
  }
}
