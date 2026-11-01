package app.awavenue.c001apk.compose.data.api

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
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

    /**
     * Shared OkHttp client: signing headers, anonymous session cookie
     * jar, and basic logging. Coil reuses this client too, because the
     * image CDN rejects requests without an app User-Agent (plain
     * requests get a 567 from TencentEdgeOne, verified 2026-11-01).
     */
    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .cookieJar(InMemoryCookieJar())
            .addInterceptor(CoolapkHeaders())
            .addInterceptor(logging)
            .build()
    }

    fun create(): CoolapkApi {
        return Retrofit.Builder()
            .baseUrl("https://api.coolapk.com/")
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(CoolapkApi::class.java)
    }
}

/**
 * Minimal in-memory cookie store: the server may set an anonymous
 * session cookie on the first response, and later requests replay it,
 * mirroring the Flutter app's session handling for logged-out browsing.
 * Nothing is persisted; the store dies with the process.
 */
private class InMemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val kept = store.getOrPut(url.host) { mutableListOf() }
        for (cookie in cookies) {
            kept.removeAll { it.name == cookie.name }
            kept += cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host].orEmpty()
}
