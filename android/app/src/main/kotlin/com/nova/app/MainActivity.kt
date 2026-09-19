package com.nova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.nova.app.data.SessionToken

// §4 native Compose + Material3, light/dark, keyboard-aware chat in screens.
// §39 appearance (system / pinned light / pinned dark) resolves here and is
// pushed through NovaTheme so every screen reads the same answer.
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val accentName = remember { mutableStateOf(AccentPreferences.get(applicationContext)) }
      val themeName = remember { mutableStateOf(AccentPreferences.getThemeMode(applicationContext)) }
      val accent = NovaAccent.entries.find { it.name.equals(accentName.value, ignoreCase = true) } ?: NovaAccent.BRONZE
      val mode = NovaThemeMode.entries.find { it.name.equals(themeName.value, ignoreCase = true) } ?: NovaThemeMode.SYSTEM
      NovaTheme(accent = accent, mode = mode) {
        NovaNav(SessionToken(applicationContext)) {
          accentName.value = AccentPreferences.get(applicationContext)
          themeName.value = AccentPreferences.getThemeMode(applicationContext)
        }
      }
    }
  }
}
