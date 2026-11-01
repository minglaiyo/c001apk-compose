package app.awavenue.c001apk.compose.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.model.FeedItem
import app.awavenue.c001apk.compose.data.model.asText
import app.awavenue.c001apk.compose.data.model.upgradedCoolapkUrl
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.JsonElement

/**
 * One shared row card for the non-feed search results (and for
 * non-feed rows mixed into topic/product tab lists), mirroring the
 * Flutter AppCard: a 40px image, one title line, one stats line.
 * Game rows render as app rows: the server sends them as apk rows
 * (verified live), and a genuine game row is the same shape.
 * Stats that the server did not send are omitted, never blank.
 */
@Composable
fun AppCard(
    item: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = item.entityType == "user"
    val image = when {
        isUser -> item.userAvatar
        !item.logo.isNullOrEmpty() -> item.logo
        else -> item.firstImage
    }
    val title = item.title?.takeIf { it.isNotEmpty() }
        ?: item.username?.takeIf { it.isNotEmpty() }
        ?: "未知"
    val stats = statsFor(item)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!image.isNullOrEmpty()) {
                AsyncImage(
                    model = image.upgradedCoolapkUrl(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(if (isUser) CircleShape else RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(if (isUser) CircleShape else RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stats.isNotEmpty()) {
                    Text(
                        text = stats.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun stat(value: String?, label: String): String? =
    value?.takeIf { it.isNotEmpty() }?.let { "$it$label" }

private fun statsFor(item: FeedItem): List<String> {
    return when (item.entityType) {
        "user" -> listOfNotNull(
            stat(item.follow.asText(), "关注"),
            stat(item.fans.asText(), "粉丝"),
            formatActiveDay(item.logintime)?.let { "${it}活跃" },
        )
        "apk", "game" -> listOfNotNull(
            stat(item.commentCount, "讨论"),
            stat(item.downCount.asText(), "下载"),
        )
        "topic" -> listOfNotNull(
            stat(item.hotNumTxt, "热度"),
            stat(item.commentCount, "讨论"),
        )
        "product" -> listOfNotNull(
            stat(item.hotNumTxt, "热度"),
            stat(item.feedCommentNumTxt, "讨论"),
        )
        else -> listOfNotNull(stat(item.commentCount, "讨论"))
    }
}

/** Last-login day as 今天 / 昨天 / yyyy-MM-dd, or null when unusable. */
private fun formatActiveDay(logintime: JsonElement?): String? {
    val seconds = logintime.asText()?.toLongOrNull() ?: return null
    if (seconds <= 0) return null
    val time = seconds * 1000
    fun dayStart(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    val diffDays = ((dayStart(System.currentTimeMillis()) - dayStart(time)) / 86400000L).toInt()
    return when {
        diffDays == 0 -> "今天"
        diffDays == 1 -> "昨天"
        diffDays > 1 -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
        else -> null
    }
}
