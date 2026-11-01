package app.awavenue.c001apk.compose.ui.feed

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.model.FeedItem
import app.awavenue.c001apk.compose.data.model.stripHtml
import app.awavenue.c001apk.compose.data.model.upgradedCoolapkUrl
import app.awavenue.c001apk.compose.data.session.AccountStore
import coil.compose.AsyncImage

/**
 * Feed list: pull-to-refresh, endless load-more, tap to detail.
 * Like the Flutter card, each item shows the title plus the message
 * body when both exist. Images load the original URLs upgraded to
 * https (the server hands out http URLs, which Android blocks); the
 * Flutter fork's .m.jpg thumbnail rewrite is a later port, not part
 * of this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedListScreen(
    viewModel: FeedViewModel,
    onItemClick: (feedId: String) -> Unit,
    onSearchClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onPublishClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    state.likeError?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeLikeError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("热门") },
                actions = {
                    IconButton(onClick = onAccountClick) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "账号",
                        )
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // Flutter hides the publish entry when logged out; the
            // handler guards again, so a stale composition can't post.
            if (AccountStore.isLoggedIn) {
                FloatingActionButton(onClick = onPublishClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "发布动态",
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.items.isEmpty() && state.isRefreshing -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.items.isEmpty() && state.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error ?: "加载失败",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text("重试")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = state.items,
                            key = { item ->
                                item.displayId?.let { "${item.entityType}:$it" }
                                    ?: item.hashCode()
                            },
                        ) { item ->
                            FeedCard(
                                item = item,
                                onClick = {
                                    item.displayId?.let { onItemClick(it) }
                                },
                                showLikeButton = AccountStore.isLoggedIn,
                                onLikeClick = { viewModel.toggleLike(item) },
                            )
                        }
                        item {
                            when {
                                state.isLoadingMore -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                state.endReached -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "没有更多了",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                state.error != null -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Button(onClick = { viewModel.loadMore() }) {
                                            Text("加载失败，点击重试")
                                        }
                                    }
                                }
                                else -> {
                                    // Trailing sentinel: composing it means
                                    // the user scrolled to the end.
                                    LaunchedEffect(Unit) {
                                        viewModel.loadMore()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
