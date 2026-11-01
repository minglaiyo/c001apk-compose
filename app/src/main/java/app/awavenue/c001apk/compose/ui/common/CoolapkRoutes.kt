package app.awavenue.c001apk.compose.ui.common

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Maps a Coolapk content URL (as carried by list rows) to an app
 * route, mirroring the Flutter onOpenLink router: the coolmarket /
 * coolapk host is stripped and the first path segment dispatches.
 * Returns null for URLs with no in-app destination. Feed, topic and
 * product routes exist; apk and user pages are not built yet, so
 * their rows land on a placeholder screen instead of dead-ending.
 */
fun routeForCardUrl(url: String?): String? {
    if (url.isNullOrEmpty()) return null
    val path = when {
        url.contains("://") -> {
            val rest = url.substringAfter("://")
            val slash = rest.indexOf('/')
            if (slash >= 0) rest.substring(slash) else "/$rest"
        }
        else -> url
    }.substringBefore('?').substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null
    return when (segments[0]) {
        "feed" -> {
            val id = segments.getOrNull(1) ?: return null
            "feed/$id"
        }
        "t" -> {
            val rawName = segments.drop(1).joinToString("/").takeIf { it.isNotEmpty() } ?: return null
            val name = try {
                URLDecoder.decode(rawName, "UTF-8")
            } catch (_: Exception) {
                rawName
            }
            "topic/" + URLEncoder.encode(name, "UTF-8")
        }
        "product" -> {
            val id = segments.getOrNull(1) ?: return null
            "product/$id"
        }
        "apk" -> {
            val pkg = segments.getOrNull(1) ?: return null
            "placeholder/apk/" + URLEncoder.encode(pkg, "UTF-8")
        }
        "u" -> {
            val uid = segments.getOrNull(1) ?: return null
            "placeholder/user/" + URLEncoder.encode(uid, "UTF-8")
        }
        else -> null
    }
}
