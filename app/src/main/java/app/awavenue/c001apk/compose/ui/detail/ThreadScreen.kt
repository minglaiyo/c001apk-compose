package app.awavenue.c001apk.compose.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.api.toggleLike
import app.awavenue.c001apk.compose.data.model.ReplyItem
import app.awavenue.c001apk.compose.data.model.asText
import app.awavenue.c001apk.compose.data.session.AccountStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Nested-reply (楼中楼) thread for one reply, mirroring the Flutter
 * fork's Reply2ReplyPage: the origin reply on top, then its nested
 * replies from GET api /v6/feed/replyList (feedType feed_reply).
 * Nested rows keep like + reply actions but do not open deeper
 * threads, matching the Flutter page (isReply2Reply hides the entry).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    api: CoolapkApi,
    origin: ReplyItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onReplyToReply: (String, String) -> Unit = { _, _ -> },
    replyPublished: Boolean = false,
    onReplyPublishedConsumed: () -> Unit = {},
) {
    var originState by remember { mutableStateOf(origin) }
    var items by remember { mutableStateOf<List<ReplyItem>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun loadPage(nextPage: Int, lastItem: String?) {
        if (loading) return
        loading = true
        error = null
        try {
            val response = api.getReply2Reply(
                id = origin.displayId.orEmpty(),
                page = nextPage,
                lastItem = lastItem,
            )
            val data = response.data.orEmpty()
            items = if (nextPage == 1) data else items + data
            page = nextPage
            if (data.isEmpty()) {
                endReached = true
                if (nextPage == 1) {
                    notice = response.message?.takeIf { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    fun toggleItemLike(item: ReplyItem) {
        val id = item.displayId ?: return
        if (!AccountStore.isLoggedIn) return
        val wasLiked = item.liked
        val oldCount = item.likenum.asText()?.toIntOrNull() ?: 0
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
        if (originState.displayId == id) {
            originState = withLike(originState, newLiked, newCount)
        } else {
            items = items.map {
                if (it.displayId == id) withLike(it, newLiked, newCount) else it
            }
        }
        scope.launch {
            val outcome = toggleLike(api, id, wasLiked, isReply = true)
            if (outcome.ok) {
                outcome.likeCount()?.toIntOrNull()?.let { serverCount ->
                    if (originState.displayId == id) {
                        originState = withLike(originState, newLiked, serverCount)
                    } else {
                        items = items.map {
                            if (it.displayId == id) withLike(it, newLiked, serverCount) else it
                        }
                    }
                }
            } else {
                if (originState.displayId == id) {
                    originState = item
                } else {
                    items = items.map { if (it.displayId == id) item else it }
                }
                Toast.makeText(
                    context,
                    outcome.message.ifEmpty { "操作失败" },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPage(1, null)
    }
    LaunchedEffect(replyPublished) {
        if (replyPublished) {
            items = emptyList()
            endReached = false
            loadPage(1, null)
            onReplyPublishedConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "共 ${originState.replyCount ?: items.size} 回复") },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ReplyRow(
                    reply = originState,
                    showActions = AccountStore.isLoggedIn,
                    onLikeClick = { toggleItemLike(originState) },
                    onReplyClick = {
                        originState.displayId?.let { id ->
                            onReplyToReply(id, originState.username.orEmpty())
                        }
                    },
                    threadEntryEnabled = false,
                )
            }
            item { HorizontalDivider() }
            items(
                items,
                key = { it.displayId ?: it.hashCode().toString() },
            ) { item ->
                ReplyRow(
                    reply = item,
                    showActions = AccountStore.isLoggedIn,
                    onLikeClick = { toggleItemLike(item) },
                    onReplyClick = {
                        item.displayId?.let { id ->
                            onReplyToReply(id, item.username.orEmpty())
                        }
                    },
                    threadEntryEnabled = false,
                )
            }
            item {
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    error != null -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = error ?: "加载失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = {
                            scope.launch {
                                loadPage(
                                    if (items.isEmpty()) 1 else page + 1,
                                    items.lastOrNull()?.displayId,
                                )
                            }
                        }) {
                            Text(text = "重试")
                        }
                    }
                    endReached -> {
                        if (items.isEmpty()) {
                            Text(
                                text = notice ?: "还没有楼中楼回复",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "没有更多了",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items.isNotEmpty() -> Button(
                        onClick = {
                            scope.launch {
                                loadPage(page + 1, items.lastOrNull()?.displayId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "加载更多")
                    }
                }
            }
        }
    }
}
