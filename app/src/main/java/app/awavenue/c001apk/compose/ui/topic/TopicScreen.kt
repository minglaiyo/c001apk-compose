package app.awavenue.c001apk.compose.ui.topic

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import app.awavenue.c001apk.compose.data.api.WriteOutcome
import app.awavenue.c001apk.compose.data.api.toWriteOutcome
import app.awavenue.c001apk.compose.data.model.TopicDetail
import app.awavenue.c001apk.compose.data.model.parseTopicDetail
import app.awavenue.c001apk.compose.data.session.AccountStore
import app.awavenue.c001apk.compose.ui.feed.TabFeedList
import kotlinx.coroutines.launch

/**
 * Topic page, mirroring the Flutter topic page: no cover/intro
 * header, just the title in the AppBar plus a scrollable tab bar.
 * Tab lists reuse TabFeedList (feed rows as FeedCard, mixed rows as
 * AppCard). The ⋮ sort menu appears on discussion tabs (tab URL
 * contains topic/tagFeedList) and rewrites sortField: 默认 restores
 * the server URL, 最新 forces dateline_desc, 热门 forces rank_score.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    api: CoolapkApi,
    topicName: String,
    onBack: () -> Unit,
    onFeedClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    onSearchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPublishClick: (String, String) -> Unit = { _, _ -> },
) {
    var detail by remember { mutableStateOf<TopicDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var tabUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var followedOverride by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun loadDetail() {
        loading = true
        error = null
        followedOverride = null
        try {
            val response = api.getTopicDetail(topicName)
            val parsed = parseTopicDetail(response.data)
            detail = parsed
            tabUrls = parsed?.tabs?.map { it.url } ?: emptyList()
            selectedTab = 0
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    fun toggleFollow() {
        if (!AccountStore.isLoggedIn) return
        val was = followedOverride ?: detail?.followed ?: false
        followedOverride = !was
        scope.launch {
            val outcome = try {
                (if (was) api.unFollowTag(topicName) else api.followTag(topicName))
                    .toWriteOutcome()
            } catch (e: Exception) {
                WriteOutcome(false, e.message ?: "操作失败", null, null)
            }
            if (!outcome.ok) {
                followedOverride = was
                Toast.makeText(
                    context,
                    outcome.message.ifEmpty { "操作失败" },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(topicName) { loadDetail() }

    val currentDetail = detail
    val effectiveFollowed = followedOverride ?: detail?.followed
    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(currentDetail?.title ?: topicName) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            },
            actions = {
                IconButton(onClick = { onSearchClick(topicName) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "话题内搜索",
                    )
                }
                val currentUrl = tabUrls.getOrNull(selectedTab)
                val isDiscussionTab = currentUrl != null &&
                    currentUrl.contains("topic/tagFeedList")
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    if (isDiscussionTab) {
                        DropdownMenuItem(
                            text = { Text("默认") },
                            onClick = {
                                val original = currentDetail?.tabs?.getOrNull(selectedTab)?.url
                                if (original != null) {
                                    tabUrls = tabUrls.toMutableList().also { it[selectedTab] = original }
                                }
                                showSortMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("最新发布") },
                            onClick = {
                                tabUrls = tabUrls.toMutableList().also {
                                    it[selectedTab] = withTopicSortField(it[selectedTab], "dateline_desc")
                                }
                                showSortMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("热门") },
                            onClick = {
                                tabUrls = tabUrls.toMutableList().also {
                                    it[selectedTab] = withTopicSortField(it[selectedTab], "rank_score")
                                }
                                showSortMenu = false
                            },
                        )
                    }
                    if (AccountStore.isLoggedIn) {
                        DropdownMenuItem(
                            text = {
                                Text(if (effectiveFollowed == true) "取消关注" else "关注")
                            },
                            onClick = {
                                toggleFollow()
                                showSortMenu = false
                            },
                        )
                    }
                }
            },
        )
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "加载失败")
                        Button(
                            onClick = { scope.launch { loadDetail() } },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            currentDetail == null || currentDetail.tabs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无内容")
                }
            }
            else -> {
                val tabs = currentDetail.tabs
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(tab.title) },
                        )
                    }
                }
                val url = tabUrls.getOrNull(selectedTab)
                if (url != null) {
                    TabFeedList(
                        api = api,
                        url = url,
                        title = tabs[selectedTab].title,
                        onFeedClick = onFeedClick,
                        onCardClick = onCardClick,
                    )
                }
            }
        }
    }
        if (AccountStore.isLoggedIn && currentDetail != null) {
            FloatingActionButton(
                onClick = {
                    onPublishClick(
                        currentDetail?.id ?: topicName,
                        currentDetail?.title ?: topicName,
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "发布动态",
                )
            }
        }
    }
}

/** Rewrites (or appends) the sortField query parameter of a topic tab URL. */
private fun withTopicSortField(tabUrl: String, sortField: String): String {
    return if (tabUrl.contains("sortField=")) {
        tabUrl.replace(Regex("sortField=[^&]*"), "sortField=$sortField")
    } else if (tabUrl.contains("?")) {
        "$tabUrl&sortField=$sortField"
    } else {
        "$tabUrl?sortField=$sortField"
    }
}
