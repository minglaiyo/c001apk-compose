package app.awavenue.c001apk.compose.data.repository

import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.api.toggleLike
import app.awavenue.c001apk.compose.data.model.FeedItem
import app.awavenue.c001apk.compose.data.model.asText
import app.awavenue.c001apk.compose.data.session.AccountStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** UI state for the feed list screen. */
data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
    val likeError: String? = null,
)

/**
 * Feed paging, mirroring the Flutter CommonController/HomeFeedController
 * shape: refresh loads page 1, load-more advances the page and passes
 * the previous page's last item id as the `lastItem` cursor.
 *
 * The skeleton renders feed items only. List responses also carry card
 * types (carousels, icon grids, title cards); those are skipped here
 * and belong to a later card-rendering step, same as the Flutter
 * entityType/entityTemplate filtering but narrowed to feeds.
 */
class FeedRepository(private val api: CoolapkApi) {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var page = 1
    private var pageUrl: String = DEFAULT_PAGE_URL

    suspend fun refresh(url: String = DEFAULT_PAGE_URL) {
        pageUrl = url
        page = 1
        _state.update {
            it.copy(isRefreshing = true, error = null, endReached = false)
        }
        try {
            val response = api.getDataList(
                url = pageUrl,
                // The Flutter HOT tab passes title '热榜' for this URL;
                // mirror it exactly instead of the empty default.
                title = if (pageUrl == DEFAULT_PAGE_URL) "热榜" else "",
                page = 1,
            )
            val feeds = response.data.orEmpty().filter { it.entityType == "feed" }
            _state.update {
                it.copy(items = feeds, isRefreshing = false)
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isRefreshing = false,
                    error = e.message ?: "加载失败，请重试",
                )
            }
        }
    }

    suspend fun loadMore() {
        val current = _state.value
        if (current.isRefreshing || current.isLoadingMore || current.endReached) return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        val nextPage = page + 1
        val lastItem = current.items.lastOrNull()?.displayId
        try {
            val response = api.getDataList(
                url = pageUrl,
                page = nextPage,
                lastItem = lastItem,
            )
            val feeds = response.data.orEmpty().filter { it.entityType == "feed" }
            page = nextPage
            _state.update {
                it.copy(
                    items = it.items + feeds,
                    isLoadingMore = false,
                    endReached = feeds.isEmpty(),
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoadingMore = false,
                    error = e.message ?: "加载更多失败",
                )
            }
        }
    }

    companion object {
        /**
         * Default tab: the Flutter home HOT tab URL, the hot ranking
         * feed. Passed as the dataList `url` parameter verbatim.
         */
        const val DEFAULT_PAGE_URL = "/page?url=V9_HOME_TAB_RANKING"
    }

    /**
     * Optimistic like toggle for one card, mirroring the Flutter
     * onLike flow: the card flips immediately, the server count wins
     * on success, and a failure restores the original item and
     * reports the message (shown as a toast by the screen).
     */
    suspend fun toggleLike(item: FeedItem) {
        val id = item.displayId ?: return
        if (!AccountStore.isLoggedIn) return
        val liked = item.liked
        val newLiked = !liked
        val oldCount = item.likenum.asText()?.toIntOrNull() ?: 0
        val newCount = (oldCount + if (newLiked) 1 else -1).coerceAtLeast(0)
        fun withLike(source: FeedItem, likedValue: Boolean, count: Int): FeedItem {
            return source.copy(
                likenum = JsonPrimitive(count),
                userAction = buildJsonObject {
                    put("like", JsonPrimitive(if (likedValue) 1 else 0))
                },
            )
        }
        _state.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.displayId == id) withLike(it, newLiked, newCount) else it
                },
            )
        }
        val outcome = toggleLike(api, id, liked, isReply = false)
        if (outcome.ok) {
            val serverCount = outcome.likeCount()?.toIntOrNull()
            if (serverCount != null) {
                _state.update { state ->
                    state.copy(
                        items = state.items.map {
                            if (it.displayId == id) withLike(it, newLiked, serverCount) else it
                        },
                    )
                }
            }
        } else {
            _state.update { state ->
                state.copy(
                    items = state.items.map { if (it.displayId == id) item else it },
                    likeError = outcome.message.ifEmpty { "操作失败" },
                )
            }
        }
    }

    fun consumeLikeError() {
        _state.update { it.copy(likeError = null) }
    }
}
