package app.awavenue.c001apk.compose.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import app.awavenue.c001apk.compose.data.model.stripHtml
import app.awavenue.c001apk.compose.data.model.upgradedCoolapkUrl
import coil.compose.AsyncImage

/**
 * One feed card, shared by the home list and the search results
 * (the Flutter app uses the same FeedCard for both). Like the
 * Flutter card, it shows the title plus the message body when both
 * exist. Images load the original URLs upgraded to https (the
 * server hands out http URLs, which Android blocks).
 */
@Composable
fun FeedCard(
    item: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Like button, following the Flutter card's gate: only rendered
    // when logged in (the caller passes showLikeButton accordingly).
    showLikeButton: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(enabled = item.displayId != null, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatar = item.userAvatar?.takeIf { it.isNotEmpty() }
                if (avatar != null) {
                    AsyncImage(
                        model = avatar.upgradedCoolapkUrl(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Column {
                    Text(
                        text = item.username?.takeIf { it.isNotEmpty() } ?: "酷友",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val typeName = item.feedTypeName?.takeIf { it.isNotEmpty() }
                    if (typeName != null) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val headline = item.headline?.stripHtml()
            // The message body is separate from the headline (Flutter
            // renders title + body); without this, feeds whose title
            // is the synthetic "XXX的动态" show no post text at all.
            val body = item.message?.stripHtml()?.takeIf { it.isNotEmpty() && it != headline }
            // The server's synthetic moment title ("XXX的动态") only
            // repeats the author line, so moment cards hide it and show
            // only the body; a real title (message_title) still shows.
            // When there is no body, the headline stays so the card is
            // not left empty.
            val redundantHeadline = !item.username.isNullOrEmpty() &&
                headline == "${item.username}的动态"
            if (!headline.isNullOrEmpty() && (!redundantHeadline || body.isNullOrEmpty())) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!body.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val image = item.firstImage
            if (image != null) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = image.upgradedCoolapkUrl(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showLikeButton && onLikeClick != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onLikeClick),
                    ) {
                        Icon(
                            imageVector = if (item.liked) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                            contentDescription = if (item.liked) "取消点赞" else "点赞",
                            tint = if (item.liked) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "赞 ${item.likeCount ?: "0"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item.likeCount?.let {
                        Text(
                            text = "赞 $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item.commentCount?.let {
                    Text(
                        text = "评论 $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
