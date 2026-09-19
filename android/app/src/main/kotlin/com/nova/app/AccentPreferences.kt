package com.nova.app

import android.content.Context

object AccentPreferences {
  private const val PREFS = "nova_prefs"
  private const val KEY_ACCENT = "accent_name"
  private const val KEY_THEME = "theme_mode"

  fun get(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(KEY_ACCENT, "bronze") ?: "bronze"

  fun set(context: Context, name: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putString(KEY_ACCENT, name).apply()
  }

  /** §39 appearance override: "system" | "light" | "dark". */
  fun getThemeMode(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(KEY_THEME, "system") ?: "system"

  fun setThemeMode(context: Context, name: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putString(KEY_THEME, name).apply()
  }
}
