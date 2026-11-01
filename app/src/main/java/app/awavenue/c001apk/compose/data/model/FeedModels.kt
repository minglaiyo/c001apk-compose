package app.awavenue.c001apk.compose.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Response envelope for Coolapk list endpoints, mirroring the Flutter
 * DataListModel: a message plus a `data` array. Unknown fields are ignored
 * (the server envelope carries more than the skeleton needs).
 */
@Serializable
data class ApiListResponse(
    val message: String? = null,
    val data: List<FeedItem>? = null,
)

/**
 * Response envelope for single-object endpoints (feed detail, product
 * detail). The skeleton only peeks at a few keys; full parsing is a
 * later step.
 */
@Serializable
data class ApiObjectResponse(
    val message: String? = null,
    val data: JsonObject? = null,
)

/**
 * One item of a Coolapk list, translated from the Flutter Datum model.
 * Only the fields the feed screen renders are kept. Several server
 * fields are dynamically typed (id/likes/comments arrive as numbers or
 * strings), so they are carried as [JsonElement] and read via [asText].
 */
@Serializable
data class FeedItem(
    val entityType: String? = null,
    val entityTemplate: String? = null,
    val title: String? = null,
    val url: String? = null,
    val pic: String? = null,
    val picArr: List<String>? = null,
    val message: String? = null,
    val messageTitle: String? = null,
    val messageCover: String? = null,
    @SerialName("cover_pic")
    val coverPic: String? = null,
    val username: String? = null,
    val userAvatar: String? = null,
    val feedTypeName: String? = null,
    // Real server key is hot_num_txt (the Flutter Datum maps it via
    // @JsonKey(name = "hot_num_txt")); the camelCase spelling parses
    // to nothing.
    @SerialName("hot_num_txt")
    val hotNumTxt: String? = null,
    val entityId: JsonElement? = null,
    val id: JsonElement? = null,
    val uid: JsonElement? = null,
    val likenum: JsonElement? = null,
    val commentnum: JsonElement? = null,
) {
    /** Stable identity for list keys, mirroring Flutter's entityType:id. */
    val displayId: String?
        get() = (id?.asText() ?: entityId?.asText())?.takeIf { it.isNotEmpty() }

    /** First image for the card: the list's pictures, else the single pic. */
    val firstImage: String?
        get() = picArr?.firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: pic?.takeIf { it.isNotEmpty() }
            ?: messageCover?.takeIf { it.isNotEmpty() }
            ?: coverPic?.takeIf { it.isNotEmpty() }

    /** Headline: the explicit title, else the message body. */
    val headline: String?
        get() = messageTitle?.takeIf { it.isNotEmpty() }
            ?: title?.takeIf { it.isNotEmpty() }
            ?: message?.takeIf { it.isNotEmpty() }

    val likeCount: String? get() = likenum.asText()
    val commentCount: String? get() = commentnum.asText()
}

/** Feed author, from the Flutter UserInfo model (subset). */
@Serializable
data class UserInfo(
    val uid: JsonElement? = null,
    val username: String? = null,
    val follow: JsonElement? = null,
    val fans: JsonElement? = null,
)

/** Reads a dynamically-typed JSON value as plain text, if it is one. */
fun JsonElement?.asText(): String? = when (this) {
    is JsonPrimitive -> if (isString) content else toString()
    else -> null
}
