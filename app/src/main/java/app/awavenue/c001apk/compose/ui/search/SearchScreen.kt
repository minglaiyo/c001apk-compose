package app.awavenue.c001apk.compose.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CaptchaChallenge
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.model.FeedItem
import app.awavenue.c001apk.compose.ui.captcha.CaptchaChallengeView
import app.awavenue.c001apk.compose.ui.feed.FeedCard
import java.net.URLDecoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One suggestion row: display title plus the keyword it searches. */
private data class SearchSuggestion(
    val title: String,
    val keyword: String,
)

/** The six search tabs, in Flutter order. */
private val SEARCH_TABS = listOf(
    "动态" to "feed",
    "应用" to "apk",
    "游戏" to "game",
    "话题" to "feedTopic",
    "数码" to "product",
    "用户" to "user",
)

/**
 * Global search, extended in stage 2: all six tabs (feed rows reuse
 * FeedCard, the other five render as AppCard), scoped search via
 * pageType/pageParam (topic pages pass tag, product pages pass
 * product_phone; scoped mode hides the tab row and shows a scope
 * subtitle), and captcha challenges on list responses. Search is
 * not captcha-gated in the common case (verified live), but a gated
 * response now offers verification instead of an empty list.
 * The feed tab's ⋮ sort/feedType menu is still pending.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    api: CoolapkApi,
    onBack: () -> Unit,
    onFeedClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    pageType: String? = null,
    pageParam: String? = null,
    modifier: Modifier = Modifier,
) {
    val scoped = pageType != null
    val scopeLabel = when (pageType) {
        "tag" -> "话题：$pageParam"
        "product_phone" -> "数码：$pageParam"
        else -> "限定搜索"
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(SearchHistoryStore.load(context)) }
    var hot by remember { mutableStateOf<List<String>?>(null) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var activeKeyword by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var resultPage by remember { mutableStateOf(1) }
    var resultEndReached by remember { mutableStateOf(false) }
    var resultLoading by remember { mutableStateOf(false) }
    var resultError by remember { mutableStateOf<String?>(null) }
    var challenge by remember { mutableStateOf<CaptchaChallenge?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Hot searches, loaded once; failure hides the section.
    LaunchedEffect(Unit) {
        hot = try {
            flattenHotKeywords(api.getHotSearch())
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Suggestions: debounce 300ms, cap 8 (Flutter parity). The
    // LaunchedEffect key cancels the in-flight request on each
    // keystroke, so a stale response can never overwrite a newer one.
    LaunchedEffect(query) {
        if (query.isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        try {
            val response = api.getSearchSuggest(query)
            suggestions = response.data.orEmpty()
                .mapNotNull { item ->
                    val title = item.title?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val keyword = keywordFromSuggestUrl(item.url) ?: title
                    SearchSuggestion(title = title, keyword = keyword)
                }
                .distinctBy { it.keyword }
                .take(8)
        } catch (_: Exception) {
            suggestions = emptyList()
        }
    }

    suspend fun loadResultPage(keyword: String, type: String, page: Int, lastItem: String?) {
        if (resultLoading) return
        resultLoading = true
        resultError = null
        try {
            val response = api.getSearch(
                type = type,
                searchValue = keyword,
                page = page,
                lastItem = lastItem,
                pageType = pageType,
                pageParam = pageParam,
            )
            val parsed = CaptchaChallenge.fromEnvelope(
                response.messageStatus,
                response.messageExtra,
            )
            if (parsed != null && page == 1) {
                challenge = parsed
                results = emptyList()
            } else {
                challenge = null
                val items = response.data.orEmpty().filter { keepForTab(it, type) }
                results = if (page == 1) items else results + items
                resultPage = page
                if (items.isEmpty()) resultEndReached = true
            }
        } catch (e: Exception) {
            resultError = e.message ?: "搜索失败"
        } finally {
            resultLoading = false
        }
    }

    fun submit(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        SearchHistoryStore.add(context, kw)
        history = SearchHistoryStore.load(context)
        activeKeyword = kw
        query = kw
        results = emptyList()
        resultPage = 1
        resultEndReached = false
        resultError = null
        challenge = null
        scope.launch { loadResultPage(kw, SEARCH_TABS[selectedTab].second, 1, null) }
    }

    fun switchTab(index: Int) {
        if (index == selectedTab) return
        selectedTab = index
        val kw = activeKeyword ?: return
        results = emptyList()
        resultPage = 1
        resultEndReached = false
        resultError = null
        challenge = null
        scope.launch { loadResultPage(kw, SEARCH_TABS[index].second, 1, null) }
    }

    val showingResults = activeKeyword != null && query == activeKeyword
    val currentType = SEARCH_TABS[selectedTab].second

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (showingResults) {
                        activeKeyword = null
                    } else {
                        onBack()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { submit(query) },
                enabled = query.isNotBlank(),
            ) {
                Text("搜索")
            }
        }
        if (scoped) {
            Text(
                text = scopeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (showingResults) {
            val keyword = activeKeyword ?: ""
            if (!scoped) {
                TabRow(selectedTabIndex = selectedTab) {
                    SEARCH_TABS.forEachIndexed { index, (label, _) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { switchTab(index) },
                            text = { Text(label) },
                        )
                    }
                }
            }
            val currentChallenge = challenge
            when {
                currentChallenge != null && results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CaptchaChallengeView(
                            challenge = currentChallenge,
                            onVerified = { scope.launch { loadResultPage(keyword, currentType, 1, null) } },
                        )
                    }
                }
                results.isEmpty() && resultLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                results.isEmpty() && resultError != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(resultError ?: "搜索失败")
                            Button(
                                onClick = { scope.launch { loadResultPage(keyword, currentType, 1, null) } },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
                results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("暂无内容")
                            Button(
                                onClick = { scope.launch { loadResultPage(keyword, currentType, 1, null) } },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            results,
                            key = { it.displayId ?: it.hashCode().toString() },
                        ) { item ->
                            if (currentType == "feed") {
                                FeedCard(
                                    item = item,
                                    onClick = { item.displayId?.let(onFeedClick) },
                                )
                            } else {
                                AppCard(
                                    item = item,
                                    onClick = { item.url?.let(onCardClick) },
                                )
                            }
                        }
                        item {
                            when {
                                resultError != null -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = resultError ?: "加载失败",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    loadResultPage(
                                                        keyword,
                                                        currentType,
                                                        resultPage + 1,
                                                        results.lastOrNull()?.displayId,
                                                    )
                                                }
                                            },
                                            modifier = Modifier.padding(top = 8.dp),
                                        ) {
                                            Text("重试")
                                        }
                                    }
                                }
                                resultLoading -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                                resultEndReached -> {
                                    Text(
                                        text = "没有更多了",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                                else -> {
                                    LaunchedEffect(results.size) {
                                        loadResultPage(
                                            keyword,
                                            currentType,
                                            resultPage + 1,
                                            results.lastOrNull()?.displayId,
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
        } else if (query.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable { submit(suggestion.keyword) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = suggestion.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else if (scoped) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "输入关键词，在${scopeLabel}内搜索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (history.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "搜索历史",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { showClearDialog = true }) {
                            Text("清除全部")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        history.forEach { word ->
                            HistoryChip(
                                word = word,
                                onClick = { submit(word) },
                                onLongClick = {
                                    SearchHistoryStore.remove(context, word)
                                    history = SearchHistoryStore.load(context)
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
                val hotList = hot
                if (!hotList.isNullOrEmpty()) {
                    Text(
                        text = "热门搜索",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        hotList.forEach { word ->
                            HistoryChip(
                                word = word,
                                onClick = { submit(word) },
                                onLongClick = null,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除全部搜索历史？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        SearchHistoryStore.clear(context)
                        history = emptyList()
                        showClearDialog = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * Whether a search row belongs to a tab's card. Card-container rows
 * (sort cards etc.) match no tab and are dropped; game rows arrive
 * as apk rows (verified live), so the game tab accepts both.
 */
private fun keepForTab(item: FeedItem, type: String): Boolean {
    return when (type) {
        "feed" -> item.entityType == "feed"
        "apk" -> item.entityType == "apk"
        "game" -> item.entityType == "apk" || item.entityType == "game"
        "feedTopic" -> item.entityType == "topic"
        "product" -> item.entityType == "product"
        "user" -> item.entityType == "user"
        else -> false
    }
}

/**
 * Pill chip for a history or hot term. Tap searches it; long-press
 * deletes a history term (hot terms have no long-press action).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryChip(
    word: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Text(
            text = word,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Suggestion rows carry searchTab://<tab>?keyword=<word> URLs
 * (verified live); the keyword is what a tap searches. Falls back
 * to the display title when the URL has no keyword parameter.
 */
private fun keywordFromSuggestUrl(url: String?): String? {
    if (url.isNullOrEmpty()) return null
    val raw = url.substringAfter("keyword=", missingDelimiterValue = "")
        .substringBefore("&")
        .takeIf { it.isNotEmpty() } ?: return null
    return try {
        URLDecoder.decode(raw, "UTF-8")
    } catch (_: Exception) {
        raw
    }
}

/**
 * Flattens the nested hot-search card payload (hotSearch /
 * searchHotListCard containers with recursive `entities`) into
 * keyword titles, mirroring the Flutter parser: sponsor rows
 * dropped, the "热门搜索" container header skipped, order kept,
 * de-duplicated, capped for display.
 */
private fun flattenHotKeywords(envelope: JsonObject): List<String> {
    val out = LinkedHashSet<String>()
    fun str(obj: JsonObject, key: String): String? {
        val element = obj[key]
        return if (element is JsonPrimitive) element.contentOrNull else null
    }
    fun walk(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                val entityType = str(element, "entityType")
                val entityTemplate = str(element, "entityTemplate")
                val sponsorType = str(element, "sponsorType")
                val isSponsor = (entityType?.startsWith("sponsor") == true) ||
                    (entityTemplate?.startsWith("sponsor") == true) ||
                    (!sponsorType.isNullOrEmpty() && sponsorType != "0")
                if (!isSponsor) {
                    val title = str(element, "title")?.takeIf { it.isNotEmpty() }
                    if (title != null && title != "热门搜索") out += title
                }
                val entities = element["entities"]
                if (entities is JsonArray) entities.forEach(::walk)
                for (key in listOf("data", "list", "items")) {
                    val child = element[key]
                    if (child is JsonArray) child.forEach(::walk)
                }
            }
            is JsonArray -> element.forEach(::walk)
            else -> {}
        }
    }
    envelope["data"]?.let(::walk)
    return out.take(12)
}
