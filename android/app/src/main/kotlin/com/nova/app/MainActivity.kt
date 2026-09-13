package com.nova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nova.app.data.SessionToken

// §4 native Compose + Material3, light/dark, keyboard-aware chat in screens.
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      NovaTheme {
        NovaNav(SessionToken(applicationContext))
      }
    }
  }
}
