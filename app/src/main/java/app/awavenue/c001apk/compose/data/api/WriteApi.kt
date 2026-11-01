package app.awavenue.c001apk.compose.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Write-operation results and the v1 image-captcha retry, ported
 * from the Flutter fork's write paths. A write succeeds when the
 * envelope carries non-null data (createFeed/reply check exactly
 * that); the message/messageStatus ride the same envelope.
 *
 * The v1 captcha is NOT the detail page's Netease v2 flow: on
 * messageStatus err_request_captcha, the caller shows an image
 * captcha dialog, validates the code via requestValidate, and the
 * original request is replayed exactly once on "验证通过".
 */
data class WriteOutcome(
    val ok: Boolean,
    val message: String,
    val messageStatus: String?,
    val data: JsonElement?,
) {
    /** New like count: feed likes nest it in data.count, reply likes return it as data. */
    fun likeCount(): String? {
        val obj = data as? JsonObject
        val nested = (obj?.get("count") as? JsonPrimitive)?.contentOrNull
        if (nested != null) return nested
        return (data as? JsonPrimitive)?.contentOrNull
    }
}

fun JsonObject.toWriteOutcome(): WriteOutcome {
    val message = (get("message") as? JsonPrimitive)?.contentOrNull.orEmpty()
    val messageStatus = (get("messageStatus") as? JsonPrimitive)?.contentOrNull
    val data = get("data")
    val ok = data != null && data !is JsonNull
    return WriteOutcome(ok, message, messageStatus, data)
}

/**
 * Runs a write attempt, retrying once through the v1 image captcha
 * when the server demands it. [requestCaptcha] shows the bitmap to
 * the user and returns the entered code, or null when cancelled.
 * Network failures inside the attempt surface as a failed outcome
 * instead of throwing, so callers only handle outcomes.
 */
suspend fun runWriteWithCaptchaV1(
    api: CoolapkApi,
    attempt: suspend () -> WriteOutcome,
    requestCaptcha: suspend (Bitmap) -> String?,
): WriteOutcome {
    suspend fun safeAttempt(): WriteOutcome {
        return try {
            attempt()
        } catch (e: Exception) {
            WriteOutcome(false, e.message ?: "请求失败", null, null)
        }
    }
    val first = safeAttempt()
    if (first.ok || first.messageStatus != "err_request_captcha") return first
    val bitmap = try {
        val response = api.captchaImage(System.currentTimeMillis())
        val bytes = response.body()?.bytes() ?: return first
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return first
    } catch (_: Exception) {
        return first
    }
    val code = requestCaptcha(bitmap) ?: return first.copy(message = "已取消验证")
    val validated = try {
        val result = api.requestValidate("err_request_captcha", code).toWriteOutcome()
        (result.data as? JsonPrimitive)?.contentOrNull == "验证通过"
    } catch (_: Exception) {
        false
    }
    if (!validated) return first.copy(message = "请输入正确的图形验证码")
    return safeAttempt()
}

/** Like/unlike toggle for feeds and replies, following the Flutter onLike selection (liked -> unlike endpoint). */
suspend fun toggleLike(
    api: CoolapkApi,
    id: String,
    liked: Boolean,
    isReply: Boolean,
): WriteOutcome {
    return try {
        val response = when {
            isReply && liked -> api.unLikeReply(id)
            isReply -> api.likeReply(id)
            liked -> api.unlikeFeed(id)
            else -> api.likeFeed(id)
        }
        response.toWriteOutcome()
    } catch (e: Exception) {
        WriteOutcome(false, e.message ?: "操作失败", null, null)
    }
}
