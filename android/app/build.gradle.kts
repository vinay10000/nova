plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("kotlinx-serialization")
}

android {
  namespace = "com.nova.app"
  compileSdk = 34
  defaultConfig {
    applicationId = "com.nova.app"
    minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "0.1.0"
  }
  buildTypes {
    release { isMinifyEnabled = false }
    debug {
      // §46: NO Gemini keys here. Only backend base URL.
      buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000\"")
    }
  }
  buildFeatures { compose = true; buildConfig = true }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.activity:activity-compose:1.9.0")
  implementation("androidx.compose.material3:material3:1.2.1")
  implementation("androidx.navigation:navigation-compose:2.7.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("androidx.room:room-ktx:2.6.1")
}
