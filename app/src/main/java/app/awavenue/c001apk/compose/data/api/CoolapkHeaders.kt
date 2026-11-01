package app.awavenue.c001apk.compose.data.api

import android.os.Build
import java.util.UUID
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Client headers for api.coolapk.com, ported from the Flutter
 * ApiInterceptor (minus authentication, see below).
 *
 * Values mirror the Flutter constants: app id com.coolapk.market,
 * version 16.2.0 / code 2604201, api version 16, locale zh-CN,
 * channel coolapk, mode universal. The User-Agent follows the
 * Flutter pattern (Dalvik + device build fields + CoolMarket suffix)
 * but is built from this device's own Build fields at runtime.
 *
 * Deliberately absent: X-App-Token. The Flutter app signs every
 * request with a v3 token derived from an embedded auth blob plus
 * bcrypt (TokenUtils.getTokenV3). That blob is secret material and is
 * NOT copied into this skeleton. Consequence, stated plainly: if the
 * server rejects unsigned requests, the feed screen shows its error
 * state instead of data, and porting the token signer is the next
 * step before real use. No login/session cookie is sent either:
 * authentication is out of scope for the skeleton.
 */
class CoolapkHeaders : Interceptor {

    // Ephemeral per-process device marker. The Flutter app uses a
    // durable encoded device id; the skeleton does not persist one.
    private val deviceMarker: String = UUID.randomUUID().toString()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent())
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Sdk-Int", Build.VERSION.SDK_INT.toString())
            .header("X-Sdk-Locale", "zh-CN")
            .header("X-App-Id", APP_ID)
            .header("X-App-Version", APP_VERSION)
            .header("X-App-Code", APP_CODE)
            .header("X-Api-Version", API_VERSION)
            .header("X-App-Device", deviceMarker)
            .header("X-Dark-Mode", "0")
            .header("X-App-Channel", "coolapk")
            .header("X-App-Mode", "universal")
            .header("X-App-Supported", APP_CODE)
            .build()
        return chain.proceed(request)
    }

    private fun userAgent(): String {
        val androidVersion = Build.VERSION.RELEASE
        val model = Build.MODEL
        val brand = Build.BRAND
        val buildId = Build.ID
        return "Dalvik/2.1.0 (Linux; U; Android $androidVersion; $model $buildId) " +
            "(#Build; $brand; $model; $buildId; $androidVersion) " +
            "+CoolMarket/$APP_VERSION-$APP_CODE-universal"
    }

    companion object {
        const val APP_ID = "com.coolapk.market"
        const val APP_VERSION = "16.2.0"
        const val APP_CODE = "2604201"
        const val API_VERSION = "16"
    }
}
