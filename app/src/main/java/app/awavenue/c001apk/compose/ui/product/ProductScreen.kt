package app.awavenue.c001apk.compose.ui.product

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.model.ProductDetail
import app.awavenue.c001apk.compose.data.model.parseProductDetail
import app.awavenue.c001apk.compose.data.model.upgradedCoolapkUrl
import app.awavenue.c001apk.compose.ui.feed.TabFeedList
import coil.compose.AsyncImage
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.launch

/**
 * Product (数码) page, mirroring the Flutter product page: the
 * AppBar title is fixed to 数码产品; the header is a collapsible
 * block (logo + title, expanded: 热度/讨论/关注, big score,
 * rating-dimension bars, spec chips); below it a scrollable tab
 * bar whose lists reuse TabFeedList. The ⋮ sort menu is shared by
 * all tabs: 默认 restores the server URLs, 最新 forces
 * dateline_desc, 热门 forces rank_score (rewritten into the inner
 * feedList query of each tab URL).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductScreen(
    api: CoolapkApi,
    productId: String,
    onBack: () -> Unit,
    onFeedClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    onSearchClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var detail by remember { mutableStateOf<ProductDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }
    var sortChoice by remember { mutableStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadDetail() {
        loading = true
        error = null
        try {
            val response = api.getProductDetail(productId)
            detail = parseProductDetail(response.data, productId)
            selectedTab = 0
            sortChoice = 0
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(productId) { loadDetail() }

    val currentDetail = detail
    val tabUrls = currentDetail?.tabs?.map { tab ->
        when (sortChoice) {
            1 -> withProductListType(tab.url, "dateline_desc")
            2 -> withProductListType(tab.url, "rank_score")
            else -> tab.url
        }
    } ?: emptyList()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("数码产品") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            },
            actions = {
                IconButton(onClick = { onSearchClick(productId) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "数码内搜索",
                    )
                }
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "排序",
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("默认") },
                        onClick = { sortChoice = 0; showSortMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("最新发布") },
                        onClick = { sortChoice = 1; showSortMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("热门") },
                        onClick = { sortChoice = 2; showSortMenu = false },
                    )
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
            currentDetail == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无内容")
                }
            }
            else -> {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val logo = currentDetail.logo
                        if (!logo.isNullOrEmpty()) {
                            AsyncImage(
                                model = logo.upgradedCoolapkUrl(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = currentDetail.title ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "收起" else "展开",
                            )
                        }
                    }
                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val stats = listOfNotNull(
                            currentDetail.hotNumTxt?.takeIf { it.isNotEmpty() }?.let { "${it}热度" },
                            currentDetail.feedCommentNumTxt?.takeIf { it.isNotEmpty() }?.let { "${it}讨论" },
                            currentDetail.followNumTxt?.takeIf { it.isNotEmpty() }?.let { "${it}关注" },
                        )
                        if (stats.isNotEmpty()) {
                            Text(
                                text = stats.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val score = currentDetail.score
                        if (score != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "%.1f".format(score),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "酷安评分" + (currentDetail.ratingCount?.let { " · ${it}人" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        currentDetail.ratingItems.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(64.dp),
                                )
                                LinearProgressIndicator(
                                    progress = { (item.score / 10.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "%.1f".format(item.score),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (currentDetail.specs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow {
                                currentDetail.specs.forEach { spec ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                                    ) {
                                        Text(
                                            text = spec,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                val tabs = currentDetail.tabs
                if (tabs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("暂无内容")
                    }
                } else {
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
    }
}

/**
 * Rewrites the inner feedList query's listType of a product tab URL
 * (the tab URL is /page?url=<encoded inner query>); adds listType
 * when the inner query has none.
 */
private fun withProductListType(tabUrl: String, listType: String): String {
    val marker = "url="
    val idx = tabUrl.indexOf(marker)
    if (idx < 0) return tabUrl
    val inner = try {
        URLDecoder.decode(tabUrl.substring(idx + marker.length), "UTF-8")
    } catch (_: Exception) {
        return tabUrl
    }
    val qIdx = inner.indexOf('?')
    if (qIdx < 0) return tabUrl
    val base = inner.substring(0, qIdx)
    val params = inner.substring(qIdx + 1).split('&')
        .filter { it.isNotEmpty() && !it.startsWith("listType=") }
        .toMutableList()
    params.add("listType=" + URLEncoder.encode(listType, "UTF-8"))
    val newInner = base + "?" + params.joinToString("&")
    return tabUrl.substring(0, idx + marker.length) + URLEncoder.encode(newInner, "UTF-8")
}
