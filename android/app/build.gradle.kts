plugins {
  alias(libs.plugins.android.application)
  // AGP 9+ has built-in Kotlin support — applying kotlin.android is a hard error.
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.nova.app"
  compileSdk = 37 // markdown-renderer 0.45.0 AAR requires 37; target stays 36 (Play deadline §1.4)
  defaultConfig {
    applicationId = "com.nova.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }
  signingConfigs.create("sandboxDebug") {
    // The default ~/.android keystore is not writable in this sandbox; keep it in-repo.
    storeFile = rootProject.file("debug.keystore")
    storePassword = "android"
    keyAlias = "androiddebugkey"
    keyPassword = "android"
  }
  buildTypes {
    release { isMinifyEnabled = false }
    debug {
      // §46: NO Gemini keys here, ever. Only our backend base URL.
      buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000\"")
      signingConfig = signingConfigs.getByName("sandboxDebug")
    }
  }
  buildFeatures { compose = true; buildConfig = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)
  implementation(libs.compose.material.icons.extended)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.navigation.compose)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.retrofit)
  implementation(libs.retrofit.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.sse)
  implementation(libs.coil.compose)
  implementation(libs.markdown.renderer.m3)
  implementation(libs.markdown.renderer.code)
  implementation(libs.markdown.renderer.coil3)
}
