package com.nova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.nova.app.data.SessionToken

private val NovaLightScheme = lightColorScheme(
  primary = Color(0xFF4F46E5),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDBE4FF),
  onPrimaryContainer = Color(0xFF1A1A2E),
  secondary = Color(0xFF6366F1),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE0E7FF),
  onSecondaryContainer = Color(0xFF1E1B4B),
  tertiary = Color(0xFF7C3AED),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFF0EAFF),
  onTertiaryContainer = Color(0xFF2D1B69),
  surface = Color(0xFFFAFBFF),
  onSurface = Color(0xFF1C1B1F),
  onSurfaceVariant = Color(0xFF6B7280),
  error = Color(0xFFDC2626),
  surfaceVariant = Color(0xFFF1F3F9),
  surfaceContainerLow = Color(0xFFF7F8FC),
  surfaceContainer = Color(0xFFEFF1F7),
  outline = Color(0xFFD1D5DB),
  outlineVariant = Color(0xFFE5E7EB),
)

private val NovaDarkScheme = darkColorScheme(
  primary = Color(0xFF818CF8),
  onPrimary = Color(0xFF1A1A2E),
  primaryContainer = Color(0xFF312E81),
  onPrimaryContainer = Color(0xFFDBE4FF),
  secondary = Color(0xFFA5B4FC),
  onSecondary = Color(0xFF1A1A2E),
  secondaryContainer = Color(0xFF3730A3),
  onSecondaryContainer = Color(0xFFE0E7FF),
  tertiary = Color(0xFFC084FC),
  onTertiary = Color(0xFF1A1A2E),
  tertiaryContainer = Color(0xFF581C87),
  onTertiaryContainer = Color(0xFFF0EAFF),
  surface = Color(0xFF0F0F14),
  onSurface = Color(0xFFF9FAFB),
  onSurfaceVariant = Color(0xFF9CA3AF),
  error = Color(0xFFF87171),
  surfaceVariant = Color(0xFF1A1B23),
  surfaceContainerLow = Color(0xFF141419),
  surfaceContainer = Color(0xFF1E1E25),
  surfaceContainerHigh = Color(0xFF282830),
  outline = Color(0xFF374151),
  outlineVariant = Color(0xFF2D2D35),
)

// §4 native Compose + Material3, light/dark, keyboard-aware chat in screens.
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MaterialTheme(colorScheme = if (isSystemInDarkTheme()) NovaDarkScheme else NovaLightScheme) {
        NovaNav(SessionToken(applicationContext))
      }
    }
  }
}
