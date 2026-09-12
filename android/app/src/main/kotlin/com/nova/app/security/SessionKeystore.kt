package com.nova.app.security

import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

// §4 Android Keystore for device-side sensitive data (session tokens only — never Gemini keys §46).
object SessionKeystore {
  fun ensureKey(alias: String = "nova_session") {
    val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    kg.init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    kg.generateKey()
  }
}
