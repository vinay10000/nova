package com.nova.app.data

import android.content.Context
import com.nova.app.BuildConfig
import com.nova.app.security.SessionKeystore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

class SessionToken(private val context: Context) {
  fun get(): String? = SessionKeystore.readToken(context)
  fun save(token: String) = SessionKeystore.saveToken(context, token)
  fun clear() = SessionKeystore.clearToken(context)
}

fun createNovaApi(session: SessionToken): NovaApi {
  val auth = Interceptor { chain ->
    val request = chain.request().newBuilder().apply {
      session.get()?.let { header("Authorization", "Bearer $it") }
    }.build()
    chain.proceed(request)
  }
  val client = OkHttpClient.Builder().addInterceptor(auth).build()
  val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
  return Retrofit.Builder()
    .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
    .client(client)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(NovaApi::class.java)
}
