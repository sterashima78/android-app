import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

val signingKeystorePath = providers.environmentVariable("ANDROID_SIGNING_KEYSTORE_PATH").orNull
val signingStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
val signingKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
  signingKeystorePath,
  signingStorePassword,
  signingKeyAlias,
  signingKeyPassword,
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningValue && !hasCompleteReleaseSigning) {
  throw GradleException("Release signing configuration is incomplete.")
}

android {
  namespace = "dev.terashima.yomitorirss"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.terashima.yomitorirss"
    minSdk = 34
    targetSdk = 36
    versionCode = 2
    versionName = "0.2.0"

    ndk {
      abiFilters += "arm64-v8a"
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  signingConfigs {
    if (hasCompleteReleaseSigning) {
      create("release") {
        storeFile = file(requireNotNull(signingKeystorePath))
        storePassword = requireNotNull(signingStorePassword)
        keyAlias = requireNotNull(signingKeyAlias)
        keyPassword = requireNotNull(signingKeyPassword)
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
    release {
      isMinifyEnabled = false
      signingConfigs.findByName("release")?.let { signingConfig = it }
      proguardFiles("proguard-rules.pro")
    }
  }

  packaging {
    resources.excludes += setOf(
      "META-INF/AL2.0",
      "META-INF/LGPL2.1",
      "META-INF/LICENSE.md",
      "META-INF/NOTICE.md",
    )
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
    unitTests.all {
      it.testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
      }
    }
  }

  lint {
    abortOnError = true
    checkReleaseBuilds = true
  }
}

// Keep release analysis (for example lintRelease) usable without secrets, but fail closed
// before any APK/AAB packaging task can produce an unsigned release artifact.
tasks.configureEach {
  val releasePackagingTask = name.lowercase() in setOf(
    "packagerelease",
    "assemblerelease",
    "bundlerelease",
  )
  if (releasePackagingTask) {
    doFirst {
      if (!hasCompleteReleaseSigning) {
        throw GradleException(
          "Release signing is required. Configure ANDROID_SIGNING_KEYSTORE_PATH and the signing credential environment variables.",
        )
      }
    }
  }
}

dependencies {
  implementation(project(":feature:ai-task-queue:domain"))
  implementation(project(":feature:ai-task-queue:data"))
  implementation(project(":feature:ai-task-queue:ui"))
  implementation(project(":feature:backup:domain"))
  implementation(project(":feature:backup:data"))
  implementation(project(":feature:backup:ui"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:bookmark:data"))
  implementation(project(":feature:bookmark:ui"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:article:data"))
  implementation(project(":feature:article:ui"))
  implementation(project(":feature:asset:domain"))
  implementation(project(":feature:asset:data"))
  implementation(project(":feature:asset:ui"))
  implementation(project(":feature:book-reader:domain"))
  implementation(project(":feature:book-reader:data"))
  implementation(project(":feature:book-reader:ui"))
  implementation(project(":feature:chat:domain"))
  implementation(project(":feature:chat:data"))
  implementation(project(":feature:chat:ui"))
  implementation(project(":feature:calendar:domain"))
  implementation(project(":feature:calendar:data"))
  implementation(project(":feature:calendar:ui"))
  implementation(project(":feature:game:domain"))
  implementation(project(":feature:game:ui"))
  implementation(project(":feature:health:domain"))
  implementation(project(":feature:health:data"))
  implementation(project(":feature:health:ui"))
  implementation(project(":feature:integrated:ui"))
  implementation(project(":feature:library:domain"))
  implementation(project(":feature:library:data"))
  implementation(project(":feature:library:ui"))
  implementation(project(":feature:knowledge:domain"))
  implementation(project(":feature:knowledge:data"))
  implementation(project(":feature:knowledge:ui"))
  implementation(project(":feature:mail:domain"))
  implementation(project(":feature:mail:data"))
  implementation(project(":feature:mail:ui"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":core:designsystem"))
  implementation(project(":core:ai-runtime"))
  implementation(project(":feature:reddit:domain"))
  implementation(project(":feature:reddit:data"))
  implementation(project(":feature:reddit:ui"))
  implementation(project(":feature:rss:domain"))
  implementation(project(":feature:rss:data"))
  implementation(project(":feature:rss:ui"))
  implementation(project(":feature:summary:domain"))
  implementation(project(":feature:summary:data"))
  implementation(project(":feature:summary:ui"))
  implementation(project(":feature:settings:domain"))
  implementation(project(":feature:settings:data"))
  implementation(project(":feature:settings:ui"))
  implementation(project(":feature:task:domain"))
  implementation(project(":feature:task:data"))
  implementation(project(":feature:task:ui"))
  implementation(project(":feature:web:domain"))
  implementation(project(":feature:web:data"))
  implementation(project(":feature:web:ui"))
  implementation(project(":feature:widget:domain"))
  implementation(project(":feature:widget:data"))
  implementation(project(":feature:widget:ui"))
  implementation(project(":feature:workout:domain"))
  implementation(project(":feature:workout:data"))
  implementation(project(":feature:workout:ui"))
  implementation(project(":feature:youtube:domain"))
  implementation(project(":feature:youtube:data"))
  implementation(project(":feature:youtube:ui"))
  implementation(project(":feature:x:domain"))
  implementation(project(":feature:x:data"))
  implementation(project(":feature:x:ui"))

  implementation(platform("androidx.compose:compose-bom:2026.06.00"))
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.activity:activity-compose:1.11.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("androidx.test:core-ktx:1.7.0")
  testImplementation("androidx.test.ext:junit:1.3.0")
}
