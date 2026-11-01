package app.awavenue.c001apk.compose.data.api

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Port of the Flutter app's CaptchaChallenge (logic/network/
 * captcha_challenge.dart, sourced from coolapk-desktop's
 * neteaseCaptcha.ts): parsing of the captcha challenge that rides
 * inside a failed API response, and formatting of the verification
 * proof. Verification proofs must never be printed or logged.
 *
 * The challenge is NOT fetched from an endpoint: a gated response
 * carries messageStatus "err_request_captcha_v2" plus messageExtra
 * (an object, or a JSON string holding one) with captchaId and
 * captchaField. A blank field selects the validate-cookie protocol;
 * a "_..." field selects the form-field protocol (the Kotlin app
 * replays via the cookie in both cases; see CaptchaProofStore).
 */
data class CaptchaChallenge(
    val captchaId: String,
    val field: String,
) {

    /**
     * Formats the Netease validate value into the proof string, or
     * null when the value is unusable (empty, too long, or contains
     * whitespace/control characters).
     */
    fun proof(validate: String): String? {
        if (validate.isEmpty() || validate.length > 8192 ||
            validate.any { it.isWhitespace() || it.code < 0x20 }
        ) {
            return null
        }
        return "NEC:${captchaId.take(8)}:$validate"
    }

    /**
     * Whether [proof] could have been produced for this challenge:
     * same captchaId prefix, plausible length, no whitespace/control
     * characters. Used to tell a rejected replay apart from a fresh
     * challenge: a gated response arriving while a matching proof is
     * attached means the server refused that proof.
     */
    fun acceptsProof(proof: String): Boolean {
        val prefix = "NEC:${captchaId.take(8)}:"
        return proof.startsWith(prefix) &&
            proof.length > 13 &&
            proof.length <= 8205 &&
            proof.none { it.isWhitespace() || it.code < 0x20 }
    }

    companion object {
        const val DEFAULT_ID = "414e5c9b866a03db03f860a1a9101672"
        private val ID_REGEX = Regex("^[a-zA-Z0-9]{16,64}$")
        private val FIELD_REGEX = Regex("^_[a-zA-Z0-9_]{1,63}$")

        /**
         * Parses a challenge from a gated response envelope, or null
         * when the response is not a captcha challenge. [envelope] is
         * the fallback source for id/field when messageExtra is absent
         * or unparsable, mirroring the Flutter implementation.
         */
        fun fromEnvelope(
            messageStatus: String?,
            messageExtra: JsonElement?,
            envelope: JsonObject? = null,
        ): CaptchaChallenge? {
            if (messageStatus != "err_request_captcha_v2") return null
            var extra: JsonObject? = null
            when (messageExtra) {
                is JsonObject -> extra = messageExtra
                is kotlinx.serialization.json.JsonPrimitive -> {
                    val text = messageExtra.content
                    extra = try {
                        Json.parseToJsonElement(text).jsonObject
                    } catch (_: Exception) {
                        null
                    }
                }
                else -> extra = null
            }
            val source = extra ?: envelope ?: return null
            fun stringField(name: String): String? {
                val element = source[name]
                return if (element is kotlinx.serialization.json.JsonPrimitive) {
                    element.content
                } else {
                    null
                }
            }
            val id = stringField("captchaId")
            val field = stringField("captchaField")
            return CaptchaChallenge(
                captchaId = if (id != null && ID_REGEX.matches(id)) id else DEFAULT_ID,
                field = when {
                    field == null -> "_v2_post_token"
                    field.trim().isEmpty() -> ""
                    FIELD_REGEX.matches(field.trim()) -> field.trim()
                    else -> "_v2_post_token"
                },
            )
        }

        /** Cookie-safe encoding of a proof, matching Flutter's Uri.encodeQueryComponent form. */
        fun encodeForCookie(proof: String): String =
            URLEncoder.encode(proof, "UTF-8")
    }
}
