package com.nova.app.security

import android.content.Context
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import java.security.KeyStore

// §4 Android Keystore for device-side sensitive data (session tokens only — never Gemini keys §46).
object SessionKeystore {
  fun ensureKey(alias: String = "nova_session") {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (store.containsAlias(alias)) return
    val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    kg.init(KeyGenParameterSpec.Builder(alias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    kg.generateKey()
  }

  fun saveToken(context: Context, token: String, alias: String = "nova_session") {
    ensureKey(alias)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key(alias))
    val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
    val payload = cipher.iv + encrypted
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString(TOKEN, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
  }

  fun readToken(context: Context, alias: String = "nova_session"): String? {
    val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null) ?: return null
    return runCatching {
      val payload = Base64.decode(encoded, Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key(alias), javax.crypto.spec.GCMParameterSpec(128, payload.copyOfRange(0, 12)))
      String(cipher.doFinal(payload.copyOfRange(12, payload.size)), StandardCharsets.UTF_8)
    }.getOrNull()
  }

  fun clearToken(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN).apply()
  }

  private fun key(alias: String): SecretKey =
    (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      .getKey(alias, null) as SecretKey)

  private const val PREFS = "nova_session"
  private const val TOKEN = "access_token"
}
