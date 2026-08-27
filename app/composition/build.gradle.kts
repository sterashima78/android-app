plugins {
  id("com.android.library")
}

android {
  namespace = "dev.terashima.yomitorirss.composition"
  compileSdk = 36

  defaultConfig { minSdk = 34 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  implementation(project(":core:background"))
  implementation(project(":core:database"))
  implementation(project(":core:ai-inference"))
  implementation(project(":core:ai-cloud-openai"))
  implementation(project(":core:ai-runtime"))
  implementation(project(":core:network"))

  implementation(project(":feature:ai-task-queue:domain")); implementation(project(":feature:ai-task-queue:data")); implementation(project(":feature:ai-task-queue:ui"))
  implementation(project(":feature:backup:domain")); implementation(project(":feature:backup:data")); implementation(project(":feature:backup:ui"))
  implementation(project(":feature:bookmark:domain")); implementation(project(":feature:bookmark:data")); implementation(project(":feature:bookmark:ui"))
  implementation(project(":feature:article:domain")); implementation(project(":feature:article:data")); implementation(project(":feature:article:ui"))
  implementation(project(":feature:asset:domain")); implementation(project(":feature:asset:data")); implementation(project(":feature:asset:ui"))
  implementation(project(":feature:book-reader:domain")); implementation(project(":feature:book-reader:data")); implementation(project(":feature:book-reader:ui"))
  implementation(project(":feature:chat:domain")); implementation(project(":feature:chat:data")); implementation(project(":feature:chat:ui"))
  implementation(project(":feature:calendar:domain")); implementation(project(":feature:calendar:data")); implementation(project(":feature:calendar:ui"))
  implementation(project(":feature:health:domain")); implementation(project(":feature:health:data")); implementation(project(":feature:health:ui"))
  implementation(project(":feature:library:domain")); implementation(project(":feature:library:data")); implementation(project(":feature:library:ui"))
  implementation(project(":feature:knowledge:domain")); implementation(project(":feature:knowledge:data")); implementation(project(":feature:knowledge:cloud-openai")); implementation(project(":feature:knowledge:ui"))
  implementation(project(":feature:mail:domain")); implementation(project(":feature:mail:data")); implementation(project(":feature:mail:ui"))
  implementation(project(":feature:reddit:domain")); implementation(project(":feature:reddit:data")); implementation(project(":feature:reddit:ui"))
  implementation(project(":feature:rss:domain")); implementation(project(":feature:rss:data")); implementation(project(":feature:rss:ui"))
  implementation(project(":feature:summary:domain")); implementation(project(":feature:summary:data")); implementation(project(":feature:summary:cloud-openai")); implementation(project(":feature:summary:ui"))
  implementation(project(":feature:settings:domain")); implementation(project(":feature:settings:data")); implementation(project(":feature:settings:ui"))
  implementation(project(":feature:task:domain")); implementation(project(":feature:task:data")); implementation(project(":feature:task:ui"))
  implementation(project(":feature:web:domain")); implementation(project(":feature:web:data")); implementation(project(":feature:web:ui"))
  implementation(project(":feature:widget:domain")); implementation(project(":feature:widget:data")); implementation(project(":feature:widget:ui"))
  implementation(project(":feature:workout:domain")); implementation(project(":feature:workout:data")); implementation(project(":feature:workout:ui"))
  implementation(project(":feature:youtube:domain")); implementation(project(":feature:youtube:data")); implementation(project(":feature:youtube:ui"))
  implementation(project(":feature:x:domain")); implementation(project(":feature:x:data")); implementation(project(":feature:x:ui"))

  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
  implementation("androidx.work:work-runtime-ktx:2.11.2")
  implementation(libs.kotlinx.coroutines.android)
}
