plugins { id("com.android.library") }

android {
  namespace = "dev.terashima.yomitorirss.feature.knowledge.data"
  compileSdk = 36
  defaultConfig { minSdk = 34 }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
  implementation(project(":feature:knowledge:domain"))
  implementation(project(":feature:bookmark:domain"))
  implementation(project(":feature:article:domain"))
  implementation(project(":feature:summary:domain"))
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":core:ai-inference"))
  implementation(project(":core:ai-cloud-openai"))
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  testImplementation("junit:junit:4.13.2")
}
