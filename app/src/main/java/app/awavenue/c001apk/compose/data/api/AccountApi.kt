package app.awavenue.c001apk.compose.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Login flow against https://account.coolapk.com, ported from the
 * Flutter fork's login page + network repo. This is a cookie-session
 * login, not OAuth: there is no grant_type and no token endpoint.
 *
 * Pre-login chain (the login button stays disabled until it
 * completes): GET /auth/login?type=mobile, then GET
 * /auth/loginByCoolApk, whose HTML Body carries a data-request-hash
 * attribute; the SESSID Set-Cookie from those responses becomes the
 * in-memory loginSession. Submit is POST /auth/loginByCoolApk with
 * the form fields below; success requires status == 1 AND
 * uid/username/token cookies in the response (credentials come from
 * cookies, not the body). This client deliberately does NOT use
 * CoolapkHeaders: the account domain takes browser-like headers and
 * no X-App-Token.
 */
object AccountApi {

    private val json = Json { ignoreUnknownKeys = true }

    private val accountJar = InMemoryCookieJar()

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .cookieJar(ForwardCookieJar(accountJar))
            .addInterceptor(AccountHeaders())
            .addInterceptor(logging)
            .build()
    }

    private val service: AccountService by lazy {
        Retrofit.Builder()
            .baseUrl("https://account.coolapk.com/")
            .client(okHttpClient)
            .build()
            .create(AccountService::class.java)
    }

    /** Clears the pre-login session, mirroring the Flutter login page resetting loginSession on open. */
    fun clearLoginSession() {
        accountJar.clear()
    }

    /** uid/username/token cookies captured by the last login submit, if all three are present. */
    fun loginCookies(): Triple<String, String, String>? {
        val url = "https://account.coolapk.com/".toHttpUrl()
        val byName = accountJar.cookiesFor(url).associateBy { it.name }
        val uid = byName["uid"]?.value
        val username = byName["username"]?.value
        val token = byName["token"]?.value
        return if (!uid.isNullOrEmpty() && !username.isNullOrEmpty() && !token.isNullOrEmpty()) {
            Triple(uid, username, token)
        } else {
            null
        }
    }

    /** Runs the two pre-login GETs and returns the requestHash, or null when the page lacks it. */
    suspend fun fetchRequestHash(): String? {
        return try {
            service.getLoginPre()
            val page = service.getLoginPage()
            val html = page.body()?.string().orEmpty()
            Regex("data-request-hash=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    /** Fetches the image captcha bytes and decodes them, or null on failure. */
    suspend fun fetchCaptchaImage(): Bitmap? {
        return try {
            val url = "https://account.coolapk.com/auth/showCaptchaImage?" +
                System.currentTimeMillis()
            val response = service.getCaptchaImage(url)
            val bytes = response.body()?.bytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    sealed interface LoginResult {
        data class Success(val uid: String, val username: String, val token: String) : LoginResult
        data class Failure(val message: String, val messageStatus: String?) : LoginResult
    }

    /** Submits the login form. randomNumber replicates TokenUtils.createRandomNumber exactly (double string with '.' replaced by 'undefined'). */
    suspend fun login(
        requestHash: String,
        account: String,
        password: String,
        captcha: String,
    ): LoginResult {
        return try {
            val randomNumber = kotlin.random.Random.nextDouble().toString()
                .replace(".", "undefined")
            val response = service.postLogin(
                submit = "1",
                randomNumber = randomNumber,
                requestHash = requestHash,
                login = account,
                password = password,
                captcha = captcha,
                code = "",
            )
            val body = response.body()?.string().orEmpty()
            val obj = try {
                json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                null
            }
            val status = obj?.get("status")?.jsonPrimitive?.content?.toIntOrNull()
            val message = obj?.get("message")?.jsonPrimitive?.content.orEmpty()
            val messageStatus = obj?.get("messageStatus")?.jsonPrimitive?.content
            if (status == 1) {
                val cookies = loginCookies()
                if (cookies != null) {
                    LoginResult.Success(cookies.first, cookies.second, cookies.third)
                } else {
                    LoginResult.Failure("登录响应缺少账号信息，请重试", messageStatus)
                }
            } else {
                LoginResult.Failure(message.ifEmpty { "登录失败，请重试" }, messageStatus)
            }
        } catch (e: Exception) {
            LoginResult.Failure("登录失败：${e.message ?: "网络错误"}", null)
        }
    }

    private interface AccountService {
        @GET("/auth/login?type=mobile")
        suspend fun getLoginPre(): retrofit2.Response<ResponseBody>

        @GET("/auth/loginByCoolApk")
        suspend fun getLoginPage(): retrofit2.Response<ResponseBody>

        @GET
        suspend fun getCaptchaImage(@Url url: String): retrofit2.Response<ResponseBody>

        @FormUrlEncoded
        @POST("/auth/loginByCoolApk")
        suspend fun postLogin(
            @Field("submit") submit: String,
            @Field("randomNumber") randomNumber: String,
            @Field("requestHash") requestHash: String,
            @Field("login") login: String,
            @Field("password") password: String,
            @Field("captcha") captcha: String,
            @Field("code") code: String,
        ): retrofit2.Response<ResponseBody>
    }

    /**
     * Browser-like headers for the account domain, mirroring the
     * Flutter interceptor's account branch. The captcha image request
     * gets image Accept + Referer; POST gets XMLHttpRequest; other
     * requests get the app id. The forward pseudo-cookie rides via
     * the jar (a manual Cookie header would suppress the jar's
     * SESSID, so it must not be set here).
     */
    private class AccountHeaders : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val builder = request.newBuilder()
                .header(
                    "sec-ch-ua",
                    "\"Android WebView\";v=\"117\", \"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"117\"",
                )
                .header("sec-ch-ua-mobile", "?1")
                .header("sec-ch-ua-platform", "Android")
                .header("Upgrade-Insecure-Requests", "1")
                .header("User-Agent", CoolapkHeaders().userAgent())
            if (request.url.encodedPath.contains("showCaptchaImage")) {
                builder.header("Accept", "image/*,*/*;q=0.8")
                    .header("Referer", "https://account.coolapk.com/auth/loginByCoolApk")
            } else {
                builder.header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                )
                if (request.method == "POST") {
                    builder.header("X-Requested-With", "XMLHttpRequest")
                } else {
                    builder.header("X-Requested-With", "com.coolapk.market")
                }
            }
            return chain.proceed(builder.build())
        }
    }

    /**
     * Appends the forward pseudo-cookie the account domain expects
     * alongside the real session cookie.
     */
    private class ForwardCookieJar(
        private val delegate: InMemoryCookieJar,
    ) : okhttp3.CookieJar {
        override fun saveFromResponse(
            url: okhttp3.HttpUrl,
            cookies: List<okhttp3.Cookie>,
        ) {
            delegate.saveFromResponse(url, cookies)
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            val cookies = delegate.loadForRequest(url).toMutableList()
            if (url.host == "account.coolapk.com") {
                okhttp3.Cookie.parse(url, "forward=https://www.coolapk.com")
                    ?.let { cookies += it }
            }
            return cookies
        }
    }
}
