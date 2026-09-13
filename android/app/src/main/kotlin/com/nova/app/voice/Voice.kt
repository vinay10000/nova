package com.nova.app.voice

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * §11 — voice is a modular capability, independent from the chat implementation.
 * Both directions sit behind interfaces so the engine (on-device vs Gemini TTS/transcribe)
 * is swappable without touching ChatViewModel.
 */

/** Speech -> text. Android stdlib SpeechRecognizer; on-device preferred, cloud fallback is the platform's own path. */
interface VoiceInput {
  val available: Boolean
  fun start()
  fun stop()
}

class AndroidVoiceInput(context: Context, private val onText: (String) -> Unit) : VoiceInput {
  private val recognizer: SpeechRecognizer? =
    if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null

  override val available: Boolean get() = recognizer != null

  override fun start() {
    val r = recognizer ?: return
    r.setRecognitionListener(object : RecognitionListener {
      override fun onResults(results: Bundle) {
        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
      }
      // Errors surface as absence of text; the mic button simply stops. No fake transcript (§61).
      override fun onError(error: Int) = Unit
      override fun onPartialResults(partialResults: Bundle) = Unit
      override fun onReadyForSpeech(params: Bundle?) = Unit
      override fun onBeginningOfSpeech() = Unit
      override fun onRmsChanged(rmsdB: Float) = Unit
      override fun onBufferReceived(buffer: ByteArray?) = Unit
      override fun onEndOfSpeech() = Unit
      override fun onEvent(eventType: Int, params: Bundle?) = Unit
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // §11: on-device first
    }
    r.startListening(intent)
  }

  override fun stop() {
    recognizer?.stopListening()
  }

  fun destroy() {
    recognizer?.destroy()
  }
}

/** Text -> audio. Android stdlib TTS; Gemini TTS swaps in behind this same interface later. */
class VoiceOutput(context: Context) {
  private var tts: TextToSpeech? = null
  private var ready = false

  init {
    tts = TextToSpeech(context) { status ->
      ready = status == TextToSpeech.SUCCESS
      if (ready) tts?.language = Locale.getDefault()
    }
  }

  fun speak(text: String) {
    if (ready) tts?.speak(text.take(2000), TextToSpeech.QUEUE_FLUSH, null, null)
  }

  fun stop() {
    tts?.stop()
  }

  fun shutdown() {
    tts?.shutdown()
  }
}

/**
 * §11 remote engine: backend /v1/tts (OpenRouter Fish Audio S2.1, WAV). Returns false on any
 * failure so the caller falls back to device TTS — the free OpenRouter tier has no
 * availability guarantees. Same interface idea as VoiceOutput, still swappable.
 */
class RemoteVoiceOutput(private val tokenProvider: () -> String?) {
  private val json = kotlinx.serialization.json.Json
  private val client = okhttp3.OkHttpClient.Builder()
    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
    .build()

  @kotlinx.serialization.Serializable private data class TtsRequest(val text: String)

  private fun cacheKey(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun cacheDir(context: Context): File =
    File(context.cacheDir, "tts").also { it.mkdirs() }

  private fun evictOldCache(dir: File, maxFiles: Int = 200) {
    val files = dir.listFiles()?.filter { it.name.endsWith(".wav") }?.sortedBy { it.lastModified() } ?: return
    if (files.size > maxFiles) {
      files.take(files.size - maxFiles).forEach { it.delete() }
    }
  }

  suspend fun speak(context: Context, text: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val token = tokenProvider() ?: return@withContext false
    val dir = cacheDir(context)
    val key = cacheKey(text)
    val cached = File(dir, "$key.wav")
    if (cached.exists() && cached.length() > 0) {
      playWav(cached)
      return@withContext true
    }
    runCatching {
      val body = json.encodeToString(TtsRequest.serializer(), TtsRequest(text.take(4000)))
        .toRequestBody("application/json".toMediaTypeOrNull())
      val req = okhttp3.Request.Builder()
        .url(com.nova.app.BuildConfig.API_BASE_URL.trimEnd('/') + "/v1/tts")
        .post(body)
        .header("Authorization", "Bearer $token")
        .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use false
        cached.writeBytes(resp.body!!.bytes())
        playWav(cached)
        evictOldCache(dir)
        true
      }
    }.getOrDefault(false)
  }

  private fun playWav(file: File) {
    MediaPlayer().apply {
      setDataSource(file.absolutePath)
      setOnCompletionListener { it.release() }
      prepare()
      start()
    }
  }
}