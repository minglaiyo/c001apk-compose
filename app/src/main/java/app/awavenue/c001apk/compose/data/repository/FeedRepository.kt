package app.awavenue.c001apk.compose.data.repository

import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.model.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** UI state for the feed list screen. */
data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
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
            val response = api.getDataList(url = pageUrl, page = 1)
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
}
