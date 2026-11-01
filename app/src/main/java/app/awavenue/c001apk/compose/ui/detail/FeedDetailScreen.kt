package app.awavenue.c001apk.compose.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.model.FeedDetail
import app.awavenue.c001apk.compose.data.model.ReplyItem
import app.awavenue.c001apk.compose.data.model.parseFeedDetail
import app.awavenue.c001apk.compose.data.model.upgradedCoolapkUrl
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Full detail screen for one feed: author header, title, body, images,
 * like/comment counts, and the reply list with paging (Flutter
 * FeedPage's detail header + reply list, minus posting, sorting and
 * nested reply threads, which are not built yet).
 *
 * The detail request itself needs no login; replies come from api2.
 * A feed whose title, body and images are all empty still shows its
 * author, counts and replies instead of a placeholder line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(
    api: CoolapkApi,
    feedId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf<FeedDetail?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detailAttempt by remember { mutableStateOf(0) }
    // Server message shown when the detail comes back empty (anonymous
    // detail requests are captcha-gated: the server answers HTTP 200
    // with message "当前访问需要验证码" and data null, so without this
    // the page would look blank for no visible reason).
    var detailNotice by remember { mutableStateOf<String?>(null) }
    var replies by remember { mutableStateOf<List<ReplyItem>>(emptyList()) }
    var replyPage by remember { mutableStateOf(1) }
    var replyEndReached by remember { mutableStateOf(false) }
    var replyLoading by remember { mutableStateOf(false) }
    var replyError by remember { mutableStateOf<String?>(null) }
    var replyNotice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadReplyPage(page: Int, lastItem: String?) {
        if (replyLoading) return
        replyLoading = true
        replyError = null
        try {
            val response = api.getFeedReply(id = feedId, page = page, lastItem = lastItem)
            val items = response.data.orEmpty()
            replies = if (page == 1) items else replies + items
            replyPage = page
            if (items.isEmpty()) {
                replyEndReached = true
                if (page == 1) {
                    replyNotice = response.message?.takeIf { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            replyError = e.message ?: "加载评论失败"
        } finally {
            replyLoading = false
        }
    }

    LaunchedEffect(feedId, detailAttempt) {
        detail = null
        detailError = null
        detailNotice = null
        replies = emptyList()
        replyPage = 1
        replyEndReached = false
        replyError = null
        replyNotice = null
        try {
            val response = api.getFeedDetail(feedId)
            val parsed = parseFeedDetail(response.data)
            detail = parsed
            if (parsed.title.isNullOrEmpty() &&
                parsed.body.isNullOrEmpty() &&
                parsed.images.isEmpty()
            ) {
                detailNotice = response.message?.takeIf { it.isNotEmpty() }
            }
            loadReplyPage(1, null)
        } catch (e: Exception) {
            detailError = e.message ?: "加载失败，请重试"
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("动态 $feedId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            detailError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(detailError ?: "加载失败")
                        Button(
                            onClick = { detailAttempt++ },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            detail == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                val current = detail!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Avatar(url = current.userAvatar, name = current.username, size = 40.dp)
                            Column {
                                Text(
                                    text = current.username ?: "匿名用户",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val meta = listOfNotNull(
                                    current.feedTypeName,
                                    formatTime(current.timeMillis),
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (!current.title.isNullOrEmpty()) {
                        item {
                            Text(
                                text = current.title,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    if (!current.body.isNullOrEmpty()) {
                        item {
                            Text(
                                text = current.body,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (current.images.isEmpty() &&
                        current.title.isNullOrEmpty() &&
                        current.body.isNullOrEmpty()
                    ) {
                        item {
                            Text(
                                // Prefer the server's own reason (e.g.
                                // the captcha gate) over the neutral line.
                                text = detailNotice
                                    ?: "这条动态没有文字和图片内容",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(current.images, key = { it }) { url ->
                        AsyncImage(
                            model = url.upgradedCoolapkUrl(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (!current.likeCount.isNullOrEmpty()) {
                                Text(
                                    text = "赞 ${current.likeCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!current.commentCount.isNullOrEmpty()) {
                                Text(
                                    text = "评论 ${current.commentCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    item { HorizontalDivider() }
                    item {
                        val total = current.commentCount ?: replies.size.toString()
                        Text(
                            text = "共 $total 回复",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    items(
                        replies,
                        key = { it.displayId ?: it.hashCode().toString() },
                    ) { reply ->
                        ReplyRow(reply)
                    }
                    item {
                        when {
                            replyError != null -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = replyError ?: "加载评论失败",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val page = if (replies.isEmpty()) {
                                                    1
                                                } else {
                                                    replyPage + 1
                                                }
                                                loadReplyPage(
                                                    page,
                                                    replies.lastOrNull()?.displayId,
                                                )
                                            }
                                        },
                                        modifier = Modifier.padding(top = 8.dp),
                                    ) {
                                        Text("重试")
                                    }
                                }
                            }
                            replyLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                            replyNotice != null -> {
                                Text(
                                    text = replyNotice ?: "",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            replyEndReached -> {
                                Text(
                                    text = "没有更多了",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> {
                                LaunchedEffect(replies.size) {
                                    loadReplyPage(
                                        replyPage + 1,
                                        replies.lastOrNull()?.displayId,
                                    )
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One reply card: avatar, author (+ replied-to user), text, image, counts. */
@Composable
private fun ReplyRow(reply: ReplyItem) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(url = reply.userAvatar, name = reply.username, size = 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val name = buildString {
                append(reply.username ?: "匿名用户")
                if (!reply.rusername.isNullOrEmpty()) {
                    append(" 回复 @${reply.rusername}")
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
            )
            if (!reply.message.isNullOrEmpty()) {
                Text(
                    text = reply.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            reply.firstImage?.let { url ->
                AsyncImage(
                    model = url.upgradedCoolapkUrl(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.FillWidth,
                )
            }
            val meta = listOfNotNull(
                reply.likeCount?.let { "赞 $it" },
                reply.replyCount?.let { "$it 条回复" },
                formatTime(reply.timeMillis),
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Circular avatar, or a tinted circle with the first character as fallback. */
@Composable
private fun Avatar(url: String?, name: String?, size: Dp) {
    if (url.isNullOrEmpty()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name?.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AsyncImage(
            model = url.upgradedCoolapkUrl(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

/** Epoch seconds (or millis) to "yyyy-MM-dd HH:mm", or null when unusable. */
private fun formatTime(millis: Long?): String? {
    if (millis == null || millis <= 0) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (_: Exception) {
        null
    }
}
