package app.awavenue.c001apk.compose.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLDecoder

/**
 * Response envelope for Coolapk list endpoints, mirroring the Flutter
 * DataListModel: a message plus a `data` array. Unknown fields are ignored
 * (the server envelope carries more than the skeleton needs). The
 * captcha fields ride the same envelope on gated lists, so list
 * screens can offer verification instead of showing an empty list.
 */
@Serializable
data class ApiListResponse(
    val message: String? = null,
    val messageStatus: String? = null,
    val messageExtra: JsonElement? = null,
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
    val messageStatus: String? = null,
    val messageExtra: JsonElement? = null,
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
    @SerialName("message_title")
    val messageTitle: String? = null,
    @SerialName("message_cover")
    val messageCover: String? = null,
    @SerialName("cover_pic")
    val coverPic: String? = null,
    val username: String? = null,
    val userAvatar: String? = null,
    val feedTypeName: String? = null,
    // AppCard fields (search apk/topic/product/user rows): logo for
    // apk/topic/product rows, download count for apk rows, follow /
    // fans / last-login for user rows.
    val logo: String? = null,
    val downCount: JsonElement? = null,
    @SerialName("feed_comment_num_txt")
    val feedCommentNumTxt: String? = null,
    val follow: JsonElement? = null,
    val fans: JsonElement? = null,
    val logintime: JsonElement? = null,
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
    // Search results carry the reply count as replynum instead of
    // commentnum; the card prefers commentnum and falls back to it.
    val replynum: JsonElement? = null,
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
    val commentCount: String? get() = commentnum.asText() ?: replynum.asText()
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

/**
 * Parsed detail of one feed, read from the /v6/feed/detail data object
 * (a Datum like the list items, plus a nested userInfo object).
 */
data class FeedDetail(
    val username: String?,
    val userAvatar: String?,
    val title: String?,
    val body: String?,
    val images: List<String>,
    val likeCount: String?,
    val commentCount: String?,
    val feedTypeName: String?,
    val timeMillis: Long?,
)

/** Reads a string field from a detail object, if it is a non-empty JSON string. */
private fun JsonObject.stringOrNull(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

private fun JsonElement?.toMillis(): Long? =
    asText()?.toLongOrNull()?.let { if (it > 1_000_000_000_000L) it else it * 1000 }

/** Parses the detail data object; missing keys stay null, never throw. */
fun parseFeedDetail(data: JsonObject?): FeedDetail {
    if (data == null) {
        return FeedDetail(null, null, null, null, emptyList(), null, null, null, null)
    }
    val userInfo = data["userInfo"] as? JsonObject
    val images = (data["picArr"] as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull?.takeIf { text -> text.isNotEmpty() }
    }
    return FeedDetail(
        username = userInfo?.stringOrNull("username")
            ?: data.stringOrNull("messageUsername"),
        userAvatar = userInfo?.stringOrNull("userAvatar")
            ?: data.stringOrNull("messageUserAvatar"),
        title = data.stringOrNull("messageTitle"),
        body = data.stringOrNull("message"),
        images = images,
        likeCount = data["likenum"].asText(),
        commentCount = data["commentnum"].asText(),
        feedTypeName = data.stringOrNull("feedTypeName"),
        timeMillis = data["dateline"].toMillis(),
    )
}

/**
 * One reply from /v6/feed/replyList (Flutter ReplyRow, subset).
 * rusername is the user being replied to, for nested replies.
 */
@Serializable
data class ReplyItem(
    val id: JsonElement? = null,
    val entityId: JsonElement? = null,
    val username: String? = null,
    val rusername: String? = null,
    val userAvatar: String? = null,
    val message: String? = null,
    val picArr: List<String>? = null,
    val likenum: JsonElement? = null,
    val replynum: JsonElement? = null,
    val dateline: JsonElement? = null,
) {
    /** Stable identity for list keys, mirroring Flutter's entityType:id. */
    val displayId: String?
        get() = (id?.asText() ?: entityId?.asText())?.takeIf { it.isNotEmpty() }

    val likeCount: String? get() = likenum.asText()
    val replyCount: String? get() = replynum.asText()
    val timeMillis: Long? get() = dateline.toMillis()
    val firstImage: String?
        get() = picArr?.firstOrNull()?.takeIf { it.isNotEmpty() }
}

/** Response envelope for the reply list endpoint. */
@Serializable
data class ApiReplyListResponse(
    val message: String? = null,
    val messageStatus: String? = null,
    val messageExtra: JsonElement? = null,
    val data: List<ReplyItem>? = null,
)

/**
 * Upgrades Coolapk http image/avatar URLs to https. The server still
 * hands out http://image.coolapk.com and http://avatar.coolapk.com
 * URLs, but Android blocks cleartext loads, so Coil would fail every
 * one of them. Both hosts serve https (verified 2026-11-01); other
 * hosts are left untouched.
 */
fun String.upgradedCoolapkUrl(): String {
    if (!startsWith("http://")) return this
    val host = substringAfter("http://").substringBefore("/")
    if (!host.endsWith(".coolapk.com")) return this
    return "https://" + substringAfter("http://")
}

/**
 * Strips HTML markup from a server text for plain-text display.
 * Coolapk message bodies can embed topic links such as
 * `<a class="feed-link-tag" href="...">#Topic#</a>`; rendering them
 * as text leaks raw tags into the UI (seen 2026-11-01). Tag text is
 * kept (only the markup is removed), `<br>` and paragraph ends
 * become newlines, and common entities are decoded. Links are not
 * made tappable: the app has no topic page to navigate to yet.
 */
fun String.stripHtml(): String {
    var text = replace(Regex("(?i)<br\\s*/?>"), "\n")
    text = text.replace(Regex("(?i)</(p|div|tr|li)>"), "\n")
    text = text.replace(Regex("<[^>]*>"), "")
    text = text.replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    text = Regex("&#(\\d+);").replace(text) {
        it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value
    }
    text = Regex("&#x([0-9a-fA-F]+);").replace(text) {
        it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: it.value
    }
    // &amp; last: decoding it earlier would double-decode "&amp;lt;".
    text = text.replace("&amp;", "&")
    return text.trim()
}

/** One tab of a topic page: title + the list URL the server hands down. */
data class TopicTab(
    val title: String,
    val url: String,
)

/** Parsed topic detail (GET /v6/topic/newTagDetail). */
data class TopicDetail(
    val title: String?,
    val logo: String?,
    val tabs: List<TopicTab>,
)

/** Parses the topic-detail data object; missing keys stay null/empty. */
fun parseTopicDetail(data: JsonObject?): TopicDetail? {
    if (data == null) return null
    val tabs = (data["tabList"] as? JsonArray).orEmpty().mapNotNull { element ->
        val tab = element as? JsonObject ?: return@mapNotNull null
        val title = tab.stringOrNull("title")
        val url = tab.stringOrNull("url")
        if (title.isNullOrEmpty() || url.isNullOrEmpty()) null else TopicTab(title, url)
    }
    return TopicDetail(
        title = data.stringOrNull("title"),
        logo = data.stringOrNull("logo"),
        tabs = tabs,
    )
}

/** One tab of a product page, kept after the Flutter filter rules. */
data class ProductTab(
    val title: String,
    val url: String,
)

/** One rating dimension row of a product header. */
data class RatingItem(
    val name: String,
    val score: Double,
)

/** Parsed product detail (GET /v6/product/detail). */
data class ProductDetail(
    val title: String?,
    val logo: String?,
    val hotNumTxt: String?,
    val feedCommentNumTxt: String?,
    val followNumTxt: String?,
    val score: Double?,
    val ratingCount: String?,
    val ratingItems: List<RatingItem>,
    val specs: List<String>,
    val tabs: List<ProductTab>,
)

private fun firstNonEmpty(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrEmpty() }

/** Decodes the inner /product/feedList?... query of a product tab URL. */
private fun productTabQuery(tabUrl: String): Map<String, String>? {
    val idx = tabUrl.indexOf("url=")
    if (idx < 0) return null
    val inner = try {
        URLDecoder.decode(tabUrl.substring(idx + 4), "UTF-8")
    } catch (_: Exception) {
        return null
    }
    val q = inner.indexOf('?')
    if (q < 0) return null
    return inner.substring(q + 1).split('&').mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq < 0) null else part.substring(0, eq) to part.substring(eq + 1)
    }.toMap()
}

/**
 * Parses the product-detail data object, mirroring the Flutter
 * ProductDetail field priorities. Tabs follow the Flutter filter:
 * drop type main/rating, drop closed tabs (is_open present but not
 * "1"), keep only tabs for this product id.
 */
fun parseProductDetail(data: JsonObject?, productId: String): ProductDetail? {
    if (data == null) return null
    val ratingItems = (data["rating_item_info"] as? JsonArray).orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val name = firstNonEmpty(item.stringOrNull("name"), item.stringOrNull("title"))
            ?: return@mapNotNull null
        val scoreText = firstNonEmpty(
            item["owner_average_score"].asText(),
            item["average_score"].asText(),
            item["v4_score"].asText(),
            item["score"].asText(),
        )
        val score = scoreText?.toDoubleOrNull() ?: return@mapNotNull null
        if (score <= 0) null else RatingItem(name, score)
    }
    val specs = (data["product_specs"] as? JsonArray).orEmpty().mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotEmpty() }
            is JsonObject -> firstNonEmpty(
                element.stringOrNull("name"),
                element.stringOrNull("title"),
            )
            else -> null
        }
    }
    val tabs = (data["tabList"] as? JsonArray).orEmpty().mapNotNull { element ->
        val tab = element as? JsonObject ?: return@mapNotNull null
        val title = tab.stringOrNull("title")
        val url = tab.stringOrNull("url")
        if (title.isNullOrEmpty() || url.isNullOrEmpty()) return@mapNotNull null
        val query = productTabQuery(url) ?: return@mapNotNull null
        val type = query["type"] ?: return@mapNotNull null
        if (type == "main" || type == "rating") return@mapNotNull null
        if (query["id"] != productId) return@mapNotNull null
        val isOpen = tab.stringOrNull("is_open")
        if (isOpen != null && isOpen != "1") return@mapNotNull null
        ProductTab(title, url)
    }
    return ProductDetail(
        title = firstNonEmpty(
            data.stringOrNull("title"),
            data.stringOrNull("index_title"),
            data.stringOrNull("alias_title"),
            data.stringOrNull("name"),
        ),
        logo = firstNonEmpty(
            data.stringOrNull("logo"),
            data.stringOrNull("product_logo"),
            data.stringOrNull("pic"),
            data.stringOrNull("icon"),
        ),
        hotNumTxt = firstNonEmpty(
            data.stringOrNull("hot_num_txt"),
            data["hot_num"].asText(),
        ),
        feedCommentNumTxt = firstNonEmpty(
            data.stringOrNull("feed_comment_num_txt"),
            data["feed_comment_num"].asText(),
        ),
        followNumTxt = firstNonEmpty(
            data.stringOrNull("follow_num_txt"),
            data["follow_num"].asText(),
        ),
        score = firstNonEmpty(
            data["owner_star_average_score"].asText(),
            data["rating_average_score"].asText(),
        )?.toDoubleOrNull(),
        ratingCount = firstNonEmpty(
            data["owner_rating_total_num"].asText(),
            data["rating_total_num"].asText(),
            data["owner_star_total_count"].asText(),
            data["star_total_count"].asText(),
        ),
        ratingItems = ratingItems,
        specs = specs,
        tabs = tabs,
    )
}
