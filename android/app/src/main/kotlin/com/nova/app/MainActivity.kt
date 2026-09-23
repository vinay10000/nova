package com.nova.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.nova.app.data.SessionToken

/**
 * §38 OAuth return leg. The backend callback redirects the browser to
 * nova://connections/{provider}/{status}?code=... — MainActivity captures the
 * incoming deep link here and NovaNav consumes it once, landing the user on
 * the Connections screen. Process-level so it survives config changes.
 */
object OAuthDeepLink {
  val pending = mutableStateOf<String?>(null)
  /** One-shot error code from a failed flow; ConnectionsScreen displays it. */
  val notice = mutableStateOf<String?>(null)
}

// §4 native Compose + Material3, light/dark, keyboard-aware chat in screens.
// §39 appearance (system / pinned light / pinned dark) resolves here and is
// pushed through NovaTheme so every screen reads the same answer.
// The ambient brush sits at the root so every transparent screen shares one
// continuous field — no hard seams between destinations.
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    captureOAuthDeepLink(intent)
    setContent {
      val accentName = remember { mutableStateOf(AccentPreferences.get(applicationContext)) }
      val themeName = remember { mutableStateOf(AccentPreferences.getThemeMode(applicationContext)) }
      val accent = NovaAccent.entries.find { it.name.equals(accentName.value, ignoreCase = true) } ?: NovaAccent.PURPLE
      val mode = NovaThemeMode.entries.find { it.name.equals(themeName.value, ignoreCase = true) } ?: NovaThemeMode.SYSTEM
      NovaTheme(accent = accent, mode = mode) {
        val ambient: Brush = novaAmbientBrush()
        Box(Modifier.fillMaxSize().background(ambient)) {
          NovaNav(SessionToken(applicationContext)) {
            accentName.value = AccentPreferences.get(applicationContext)
            themeName.value = AccentPreferences.getThemeMode(applicationContext)
          }
        }
      }
    }
  }

  // singleTask + onNewIntent: the OAuth return arrives while the app already
  // lives in the background (the browser handed the deep link back to us).
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    captureOAuthDeepLink(intent)
  }

  private fun captureOAuthDeepLink(intent: Intent?) {
    val uri = intent?.data ?: return
    if (uri.scheme == "nova" && uri.host == "connections") {
      OAuthDeepLink.pending.value = uri.toString()
      // Consume the data so an activity recreate (rotation) doesn't replay it.
      intent.setData(null)
    }
  }
}
