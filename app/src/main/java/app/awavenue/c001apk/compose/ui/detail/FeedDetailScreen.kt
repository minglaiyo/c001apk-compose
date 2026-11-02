package app.awavenue.c001apk.compose.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CaptchaChallenge
import app.awavenue.c001apk.compose.data.api.CaptchaProofStore
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.api.toggleLike
import app.awavenue.c001apk.compose.data.model.FeedDetail
import app.awavenue.c001apk.compose.data.model.ReplyItem
import app.awavenue.c001apk.compose.data.model.asText
import app.awavenue.c001apk.compose.data.model.parseFeedDetail
import app.awavenue.c001apk.compose.data.model.stripHtml
import app.awavenue.c001apk.compose.data.model.upgradedCoolapkUrl
import app.awavenue.c001apk.compose.data.session.AccountStore
import app.awavenue.c001apk.compose.ui.captcha.CaptchaWebView
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
    onReplyClick: () -> Unit = {},
    onReplyToReply: (String, String) -> Unit = { _, _ -> },
    onThreadClick: (ReplyItem) -> Unit = {},
    replyPublished: Boolean = false,
    onReplyPublishedConsumed: () -> Unit = {},
) {
    var detail by remember { mutableStateOf<FeedDetail?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var detailAttempt by remember { mutableStateOf(0) }
    // Server message shown when the detail comes back empty (anonymous
    // detail requests are captcha-gated: the server answers HTTP 200
    // with message "当前访问需要验证码" and data null, so without this
    // the page would look blank for no visible reason).
    var detailNotice by remember { mutableStateOf<String?>(null) }
    // Captcha challenge parsed from a gated response, if any, plus the
    // verification UI state. The detail request itself never prompts
    // on its own (mirroring Flutter's verify:false default for detail):
    // the user taps 去验证 explicitly.
    var captchaChallenge by remember { mutableStateOf<CaptchaChallenge?>(null) }
    var replyChallenge by remember { mutableStateOf<CaptchaChallenge?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var replies by remember { mutableStateOf<List<ReplyItem>>(emptyList()) }
    var replyPage by remember { mutableStateOf(1) }
    var replyEndReached by remember { mutableStateOf(false) }
    var replyLoading by remember { mutableStateOf(false) }
    var replyError by remember { mutableStateOf<String?>(null) }
    var replyNotice by remember { mutableStateOf<String?>(null) }
    var detailLiked by remember { mutableStateOf(false) }
    var detailLikeCount by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                    replyChallenge = CaptchaChallenge.fromEnvelope(
                        response.messageStatus, response.messageExtra,
                    )
                }
            } else if (page == 1) {
                replyChallenge = null
            }
        } catch (e: Exception) {
            replyError = e.message ?: "加载评论失败"
        } finally {
            replyLoading = false
        }
    }

    fun toggleDetailLike() {
        if (!AccountStore.isLoggedIn) return
        val wasLiked = detailLiked
        val oldCount = detailLikeCount?.toIntOrNull() ?: 0
        detailLiked = !wasLiked
        detailLikeCount = ((oldCount + if (!wasLiked) 1 else -1).coerceAtLeast(0)).toString()
        scope.launch {
            val outcome = toggleLike(api, feedId, wasLiked, isReply = false)
            if (outcome.ok) {
                outcome.likeCount()?.let { detailLikeCount = it }
            } else {
                detailLiked = wasLiked
                detailLikeCount = oldCount.toString()
                Toast.makeText(
                    context,
                    outcome.message.ifEmpty { "操作失败" },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun toggleReplyLike(reply: ReplyItem) {
        val id = reply.displayId ?: return
        if (!AccountStore.isLoggedIn) return
        val wasLiked = reply.liked
        val oldCount = reply.likenum.asText()?.toIntOrNull() ?: 0
        val newLiked = !wasLiked
        val newCount = (oldCount + if (newLiked) 1 else -1).coerceAtLeast(0)
        fun withLike(source: ReplyItem, likedValue: Boolean, count: Int): ReplyItem {
            return source.copy(
                likenum = JsonPrimitive(count),
                userAction = buildJsonObject {
                    put("like", JsonPrimitive(if (likedValue) 1 else 0))
                },
            )
        }
        replies = replies.map { if (it.displayId == id) withLike(it, newLiked, newCount) else it }
        scope.launch {
            val outcome = toggleLike(api, id, wasLiked, isReply = true)
            if (outcome.ok) {
                outcome.likeCount()?.toIntOrNull()?.let { serverCount ->
                    replies = replies.map {
                        if (it.displayId == id) withLike(it, newLiked, serverCount) else it
                    }
                }
            } else {
                replies = replies.map { if (it.displayId == id) reply else it }
                Toast.makeText(
                    context,
                    outcome.message.ifEmpty { "操作失败" },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(replyPublished) {
        if (replyPublished) {
            loadReplyPage(1, null)
            onReplyPublishedConsumed()
        }
    }

    LaunchedEffect(feedId, detailAttempt) {
        detail = null
        detailError = null
        detailNotice = null
        captchaChallenge = null
        replyChallenge = null
        verifying = false
        replies = emptyList()
        replyPage = 1
        replyEndReached = false
        replyError = null
        replyNotice = null
        try {
            val response = api.getFeedDetail(feedId)
            val parsed = parseFeedDetail(response.data)
            detail = parsed
            detailLiked = false
            detailLikeCount = parsed.likeCount
            if (parsed.title.isNullOrEmpty() &&
                parsed.body.isNullOrEmpty() &&
                parsed.images.isEmpty()
            ) {
                detailNotice = response.message?.takeIf { it.isNotEmpty() }
                val challenge = CaptchaChallenge.fromEnvelope(
                    response.messageStatus, response.messageExtra,
                )
                captchaChallenge = challenge
                // A gated response arriving while a matching proof is
                // attached means the server refused that proof: drop it
                // (mirroring Flutter's invalidation) and say so plainly.
                val proof = CaptchaProofStore.validProof()
                if (challenge != null && proof != null && challenge.acceptsProof(proof)) {
                    CaptchaProofStore.clear()
                    detailNotice = "验证已完成，但服务端仍未允许读取详情，可稍后重试"
                }
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
        floatingActionButton = {
            if (AccountStore.isLoggedIn) {
                FloatingActionButton(onClick = onReplyClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "回复",
                    )
                }
            }
        },
    ) { padding ->
        val verifyChallenge = captchaChallenge ?: replyChallenge
        if (verifying && verifyChallenge != null) {
            CaptchaWebView(
                challenge = verifyChallenge,
                onVerified = { validate ->
                    verifyChallenge.proof(validate)?.let { CaptchaProofStore.save(it) }
                    verifying = false
                    detailAttempt++
                },
                onCancel = { verifying = false },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else when {
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
                    if (!current.title?.stripHtml().isNullOrEmpty()) {
                        item {
                            Text(
                                text = current.title?.stripHtml() ?: "",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    if (!current.body?.stripHtml().isNullOrEmpty()) {
                        item {
                            Text(
                                text = current.body?.stripHtml() ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (current.images.isEmpty() &&
                        current.title?.stripHtml().isNullOrEmpty() &&
                        current.body?.stripHtml().isNullOrEmpty()
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
                        if (captchaChallenge != null) {
                            item {
                                Button(
                                    onClick = { verifying = true },
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Text("去验证")
                                }
                            }
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (AccountStore.isLoggedIn) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { toggleDetailLike() },
                                ) {
                                    Icon(
                                        imageVector = if (detailLiked) {
                                            Icons.Default.Favorite
                                        } else {
                                            Icons.Default.FavoriteBorder
                                        },
                                        contentDescription = if (detailLiked) "取消点赞" else "点赞",
                                        tint = if (detailLiked) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(
                                        text = "赞 ${detailLikeCount ?: current.likeCount ?: "0"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else if (!current.likeCount.isNullOrEmpty()) {
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
                        ReplyRow(
                            reply = reply,
                            showActions = AccountStore.isLoggedIn,
                            onLikeClick = { toggleReplyLike(reply) },
                            onReplyClick = {
                                reply.displayId?.let { id ->
                                    onReplyToReply(id, reply.username.orEmpty())
                                }
                            },
                            onThreadClick = { onThreadClick(reply) },
                        )
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
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = replyNotice ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (replyChallenge != null) {
                                        Button(
                                            onClick = { verifying = true },
                                            modifier = Modifier.padding(top = 8.dp),
                                        ) {
                                            Text("去验证")
                                        }
                                    }
                                }
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
internal fun ReplyRow(
    reply: ReplyItem,
    showActions: Boolean = false,
    onLikeClick: () -> Unit = {},
    onReplyClick: () -> Unit = {},
    onThreadClick: () -> Unit = {},
    threadEntryEnabled: Boolean = true,
) {
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
            if (!reply.message?.stripHtml().isNullOrEmpty()) {
                Text(
                    text = reply.message?.stripHtml() ?: "",
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
            if (showActions) {
                val threadCount = reply.replyCount?.toIntOrNull()
                val threadTime = formatTime(reply.timeMillis)
                val countModifier = if (threadEntryEnabled) {
                    Modifier.clickable(onClick = onThreadClick)
                } else {
                    Modifier
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (threadCount != null && threadCount > 0) {
                        Text(
                            text = "$threadCount 条回复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = countModifier,
                        )
                        if (threadTime != null) {
                            Text(
                                text = " · $threadTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val meta = listOfNotNull(
                            reply.replyCount?.let { "$it 条回复" },
                            threadTime,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onLikeClick),
                    ) {
                        Icon(
                            imageVector = if (reply.liked) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                            contentDescription = if (reply.liked) "取消点赞" else "点赞",
                            tint = if (reply.liked) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "赞 ${reply.likeCount ?: "0"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "回复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onReplyClick),
                    )
                }
            } else {
                val plainCount = reply.replyCount?.toIntOrNull()
                val plainTime = formatTime(reply.timeMillis)
                val plainCountModifier = if (threadEntryEnabled) {
                    Modifier.clickable(onClick = onThreadClick)
                } else {
                    Modifier
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val likePart = reply.likeCount?.let { "赞 $it" }
                    if (likePart != null) {
                        Text(
                            text = likePart,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (plainCount != null && plainCount > 0) {
                        if (likePart != null) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "$plainCount 条回复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = plainCountModifier,
                        )
                        if (plainTime != null) {
                            Text(
                                text = " · $plainTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val rest = listOfNotNull(
                            reply.replyCount?.let { "$it 条回复" },
                            plainTime,
                        ).joinToString(" · ")
                        if (rest.isNotEmpty()) {
                            if (likePart != null) {
                                Text(
                                    text = " · ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = rest,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
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
internal fun formatTime(millis: Long?): String? {
    if (millis == null || millis <= 0) return null
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (_: Exception) {
        null
    }
}
