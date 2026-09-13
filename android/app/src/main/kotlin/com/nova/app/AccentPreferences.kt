package com.nova.app

import android.content.Context

object AccentPreferences {
  private const val PREFS = "nova_prefs"
  private const val KEY_ACCENT = "accent_name"

  fun get(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .getString(KEY_ACCENT, "bronze") ?: "bronze"

  fun set(context: Context, name: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      .edit().putString(KEY_ACCENT, name).apply()
  }
}
