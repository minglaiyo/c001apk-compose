package app.awavenue.c001apk.compose.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CaptchaChallenge
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.model.FeedItem
import app.awavenue.c001apk.compose.ui.captcha.CaptchaChallengeView
import app.awavenue.c001apk.compose.ui.search.AppCard
import kotlinx.coroutines.launch

/**
 * Shared tab list for topic and product pages: one server tab URL
 * (passed verbatim, including sort rewrites) rendered as a paged
 * list. Feed rows reuse FeedCard; apk/game/topic/product/user rows
 * render as AppCard; card-container rows (sort cards etc.) are
 * skipped, mirroring the Flutter item dispatch. Reloads from page 1
 * whenever [url] changes (tab switch or sort change).
 */
@Composable
fun TabFeedList(
    api: CoolapkApi,
    url: String,
    title: String,
    onFeedClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var challenge by remember { mutableStateOf<CaptchaChallenge?>(null) }

    suspend fun loadPage(p: Int, lastItem: String?) {
        if (loading) return
        loading = true
        error = null
        try {
            val response = api.getDataList(url, title, p, null, lastItem)
            val parsed = CaptchaChallenge.fromEnvelope(
                response.messageStatus,
                response.messageExtra,
            )
            if (parsed != null && p == 1) {
                challenge = parsed
                items = emptyList()
            } else {
                challenge = null
                val rows = response.data.orEmpty().filter { it.entityType != "card" }
                items = if (p == 1) rows else items + rows
                page = p
                if (rows.isEmpty()) endReached = true
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(url) {
        items = emptyList()
        page = 1
        endReached = false
        error = null
        challenge = null
        loadPage(1, null)
    }

    val currentChallenge = challenge
    when {
        currentChallenge != null && items.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CaptchaChallengeView(
                    challenge = currentChallenge,
                    onVerified = { scope.launch { loadPage(1, null) } },
                )
            }
        }
        items.isEmpty() && loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        items.isEmpty() && error != null -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "加载失败")
                    Button(
                        onClick = { scope.launch { loadPage(1, null) } },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("重试")
                    }
                }
            }
        }
        items.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无内容")
                    Button(
                        onClick = { scope.launch { loadPage(1, null) } },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("重试")
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items,
                    key = { it.displayId?.let { id -> "${it.entityType}:$id" } ?: it.hashCode() },
                ) { item ->
                    when (item.entityType) {
                        "feed" -> FeedCard(
                            item = item,
                            onClick = { item.displayId?.let(onFeedClick) },
                        )
                        "apk", "game", "topic", "product", "user" -> AppCard(
                            item = item,
                            onClick = { item.url?.let(onCardClick) },
                        )
                        else -> {}
                    }
                }
                item {
                    when {
                        error != null -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = error ?: "加载失败",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = {
                                        scope.launch {
                                            loadPage(page + 1, items.lastOrNull()?.displayId)
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                        loading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        endReached -> {
                            Text(
                                text = "没有更多了",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        else -> {
                            LaunchedEffect(items.size) {
                                loadPage(page + 1, items.lastOrNull()?.displayId)
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
