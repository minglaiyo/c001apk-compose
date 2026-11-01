package app.awavenue.c001apk.compose.data.api

import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import org.mindrot.jbcrypt.BCrypt

/**
 * Port of the Flutter app's TokenUtils.getTokenV3 (Dart) plus its device-code
 * format, so anonymous browsing works: api.coolapk.com requires the signed
 * X-App-Token header on every request, even for public content.
 *
 * The lookup blob below is the same protocol data the Flutter app ships in
 * token_v3_data.dart (sourced from coolapk-desktop's public protocol data):
 * client signing material from the open-source clients, not any user's
 * credential. No account is involved: no uid, no login token, no session
 * cookie of any user is sent or stored. The device code is generated fresh
 * per process from a random id plus this device's own Build fields, in the
 * Flutter format; nothing is persisted yet.
 */
object TokenV3 {

    private const val TOKEN_V3_DATA = "VFRCVU9GUXNRMEVsTFVNa1dERWpRU0VzVXlFbUxETkFVeTFEUEZZdUl5MGlNVU01SXpCVUpGY3RNeWtoTVVRMUpTMURPU1V4TXpoUUxWTXdWMDBzSXloU01UUXNXVEZEUkZFd1EwUlZNU1FrVVMwaktTTXRKRGhZTFZRa1VqRXpLU1FzTTBCVkxUUXdVaTAwTlNNc00yQlhMVk1sSkMxRE5GbE5MQ1EwV1RFekxTUXhRMEVoTEVNbElqRXpLRmN3TXowbExpTkVWekVqUFNZdU5Ea2tMRk13Vml4VEtGa3RRemhYTEZNc1dDNGpMU1l0VTJCVFRUQTBMRkV1TXpoVExqTWhJakZETUZNc0kwQlZMQ1FrVUN3ekpTSXNJekJTTUVNOFVDeEVMU0l0STJCUUxVUW9VeTBqTEZNc0l5VWtNRlF3VmswdFJDeFlNVE1sSkM0ME5TRXdVMFJRTEZNaElpMGtOU013VTJCUUxqTmdVeXdqT0ZFc05DeFRMRU13V0N4VUxTRXRJeVJUTEROQkl5MDBKRlpOTVVNc1VEQkVKRmd0TkMwa0xEUWtXQzFETUZNeFJDa2pMVk0wVml4VE1GZ3hJeTBtTFZNeEpTMHpKRlF4TTJCUUxTUTRXVEJETUZNdUkwVWhUU3d6UEZndFJEQlJNRE5FVVMwak9GWXVKRGtsTVNRMFV6QXpOU013UkRra01UTWtWU3hVTUZRdFJDeFlNVE5GSXl4RUxGWXNJelVoTGlRMFYwMHhOQ3hWTENNMFZ5NHpKRlV4TkN4VExGUTRWeTRqWUZZeEpEVWpMVU13VXpGRFBGSXNNMEVsTERNc1ZEQTBPU011TkNrbUxFUWtWaXhFTVNKTkxUTW9VaXd6TEZjdFUwUlFMaVF3VlRGREpTVXdVeWtqTVVNaEl5MHpJU1VzSTBFak1UTXdVUzFVTkZjeFJERWxMVVF0SlM0a05GSXNJemhaVFRCVFlGRXdReVJZTFNNbElTNDBMRmdzVkRSWkxVUW9XVEJEUUZZdFJDeFdMak5FVkRCRE1Ga3RORFVsTEZRNFZTMVVKRmd3TXpSV0xEUTRVVTB3UXkwbUxVUTRXUzFUTVNNc1V5VWlNVU5BVVN4VUxGWXVJMFJVTGlNc1dERkVMRk14STJCUUxWTTBXUzRrT0Zjd00wUlhMVk1vV0MwaktGRXhMQ00wVml4VE5GQXRSRFJXTFVRbEpDMVRKRkF3TkN4Z1lB"

    private const val APP_ID = "com.coolapk.market"
    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val SALT_REGEX = Regex("^[./A-Za-z0-9]{22}$")

    /** Per-process device code in the Flutter format. */
    val deviceCode: String by lazy { generateDeviceCode() }

    /**
     * Derives a fresh v3 request signature, exactly like the Dart version:
     * tries time offsets 0..15 until the bcrypt salt passes the charset
     * check, then returns "v3" + base64 of the "$2y" bcrypt hash.
     */
    fun getTokenV3(deviceCode: String, appCode: Int = 2604201): String {
        val paddedBlob = TOKEN_V3_DATA +
            "=".repeat((4 - TOKEN_V3_DATA.length % 4) % 4)
        val blob = Base64.decode(paddedBlob, Base64.DEFAULT)
        val now = System.currentTimeMillis() / 1000
        for (offset in 0..15) {
            val time = now + offset
            val index = (((time + appCode) % 100) * 4 + 0x80).toInt()
            val segmentAscii =
                String(blob.copyOfRange(index, index + 0x80), Charsets.US_ASCII)
            val segment =
                String(Base64.decode(segmentAscii, Base64.DEFAULT), Charsets.UTF_8)
            val plain =
                "$APP_ID&$segment&${md5(deviceCode)}&$time&$appCode"
            val password = md5(base64NoPad(plain))
            val source = base64NoPad("${time.toString(16)}/${md5(plain)}")
                .substring(0, 22)
            val adjusted = ((ALPHABET.indexOf(source[21]) - 5) % 64 + 64) % 64
            val salt = source.substring(0, 21) + ALPHABET[adjusted]
            if (!SALT_REGEX.matches(salt)) continue
            val hash = BCrypt.hashpw(password, "\$2a\$04\$$salt")
            val hash2y = "\$2y" + hash.substring(3)
            return "v3" + base64NoPad(hash2y)
        }
        throw IllegalStateException("无法生成请求签名，请检查系统时间")
    }

    private fun generateDeviceCode(): String {
        val randomHex = ByteArray(16)
            .also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val fields = listOf(
            "ca_$randomHex",
            " ",
            " ",
            " ",
            " ${Build.MANUFACTURER}",
            " ${Build.BRAND}",
            " ${Build.MODEL}",
            " ${Build.ID}",
            " null",
        )
        return encodeDevice(fields.joinToString(";"))
    }

    private fun encodeDevice(deviceInfo: String): String {
        val base64Str = Base64.encodeToString(
            deviceInfo.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )
        return base64Str.reversed().replace(Regex("[\\r\\n=]"), "")
    }

    private fun base64NoPad(input: String): String =
        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            .replace("=", "")

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
