plugins {
  alias(libs.plugins.android.application)
  // AGP 9+ has built-in Kotlin support — applying kotlin.android is a hard error.
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
  namespace = "com.nova.app"
  // 37 + minor 1: the A2UI artifacts (androidx.a2ui.compose:*, material3-a2ui)
  // are built against API 37.1 and their AAR metadata rejects a lower minor.
  // targetSdk stays 36 (Play deadline §1.4) — a compileSdk minor opts into no
  // new runtime behaviour.
  compileSdk = 37
  compileSdkMinor = 1
  defaultConfig {
    applicationId = "com.nova.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  signingConfigs.create("sandboxDebug") {
    // The default ~/.android keystore is not writable in this sandbox; keep it in-repo.
    storeFile = rootProject.file("debug.keystore")
    storePassword = "android"
    keyAlias = "androiddebugkey"
    keyPassword = "android"
  }
  signingConfigs.create("preview") {
    // Dedicated keystore for shareable preview builds (same key = stable update identity).
    storeFile = rootProject.file("preview.keystore")
    storePassword = "novapreview2026"
    keyAlias = "novapreview"
    keyPassword = "novapreview2026"
  }
  buildTypes {
    // §46: NO Gemini keys here, ever. Only our backend base URL (all build types).
    all {
      buildConfigField("String", "API_BASE_URL", "\"https://nova-backend-beige.vercel.app\"")
    }
    release {
      // Preview sharing: no obfuscation so friend-reported stack traces stay readable.
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("preview")
    }
    debug {
      // Emulator reaches the host machine via 10.0.2.2 — exercise local §45 cards.
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

  // Agent-to-UI (A2UI): agent-generated surfaces rendered as native Compose.
  implementation(libs.a2ui.compose.runtime)
  implementation(libs.a2ui.compose.ui)
  implementation(libs.a2ui.material3)

  testImplementation("junit:junit:4.13.2")

  androidTestImplementation("androidx.test:runner:1.7.0")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.compose.ui.test.junit4)
  // Supplies the test activity the Compose test harness needs. Debug-only, and
  // merged into the test APK rather than the app.
  debugImplementation(libs.compose.ui.test.manifest)
  androidTestImplementation(libs.a2ui.compose.ui.testing)
}
