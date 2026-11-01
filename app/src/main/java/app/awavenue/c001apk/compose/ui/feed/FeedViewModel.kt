package app.awavenue.c001apk.compose.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.awavenue.c001apk.compose.data.model.FeedItem
import app.awavenue.c001apk.compose.data.repository.FeedRepository
import kotlinx.coroutines.launch

/** Thin bridge between [FeedRepository] and the feed list screen. */
class FeedViewModel(private val repository: FeedRepository) : ViewModel() {

    val state = repository.state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun loadMore() {
        viewModelScope.launch { repository.loadMore() }
    }

    fun toggleLike(item: FeedItem) {
        viewModelScope.launch { repository.toggleLike(item) }
    }

    fun consumeLikeError() {
        repository.consumeLikeError()
    }
}

class FeedViewModelFactory(
    private val repository: FeedRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FeedViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return FeedViewModel(repository) as T
    }
}
