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
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.model.FeedItem
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

/**
 * Global search, stage 1 of the full rewrite: history + hot
 * searches + suggestions + the 动态 results tab. The 动态 tab
 * reuses FeedItem + FeedCard outright (search results are full
 * feed items, verified against the live API); the other tabs
 * (应用/游戏/话题/数码/用户) need their own cards and come later.
 * Search is not captcha-gated (verified: HTTP 200 with real data),
 * so no verification UI is wired here yet.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    api: CoolapkApi,
    onBack: () -> Unit,
    onFeedClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(SearchHistoryStore.load(context)) }
    var hot by remember { mutableStateOf<List<String>?>(null) }
    var suggestions by remember { mutableStateOf<List<SearchSuggestion>>(emptyList()) }
    var activeKeyword by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var resultPage by remember { mutableStateOf(1) }
    var resultEndReached by remember { mutableStateOf(false) }
    var resultLoading by remember { mutableStateOf(false) }
    var resultError by remember { mutableStateOf<String?>(null) }
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

    suspend fun loadResultPage(keyword: String, page: Int, lastItem: String?) {
        if (resultLoading) return
        resultLoading = true
        resultError = null
        try {
            val response = api.getSearch(
                type = "feed",
                searchValue = keyword,
                page = page,
                lastItem = lastItem,
            )
            val items = response.data.orEmpty().filter { it.entityType == "feed" }
            results = if (page == 1) items else results + items
            resultPage = page
            if (items.isEmpty()) resultEndReached = true
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
        scope.launch { loadResultPage(kw, 1, null) }
    }

    val showingResults = activeKeyword != null && query == activeKeyword

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

        if (showingResults) {
            val keyword = activeKeyword ?: ""
            when {
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
                                onClick = { scope.launch { loadResultPage(keyword, 1, null) } },
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
                                onClick = { scope.launch { loadResultPage(keyword, 1, null) } },
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
                            FeedCard(
                                item = item,
                                onClick = { item.displayId?.let(onFeedClick) },
                            )
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
