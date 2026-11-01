package app.awavenue.c001apk.compose.data.api

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Builds the Retrofit client for https://api.coolapk.com (the Flutter
 * URL_API_SERVICE host). Lenient JSON: unknown server fields are
 * ignored and explicit nulls fall back to field defaults, because
 * Coolapk list items carry far more keys than the skeleton models.
 */
object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun create(): CoolapkApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(CoolapkHeaders())
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.coolapk.com/")
            .client(client)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(CoolapkApi::class.java)
    }
}
