package app.awavenue.c001apk.compose.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Reads a string field from a detail object, if it is a JSON string. */
private fun JsonObject?.stringOrNull(key: String): String? =
    this?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull }

private sealed interface DetailState {
    data object Loading : DetailState
    data class Error(val message: String) : DetailState
    data class Loaded(val title: String?, val body: String?) : DetailState
}

/**
 * Detail placeholder screen. It already calls the verified endpoint
 * (GET /v6/feed/detail?id=) and shows the returned title/body when
 * present; full detail rendering (author header, media gallery,
 * replies) is the next step, not part of the skeleton.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedDetailScreen(
    api: CoolapkApi,
    feedId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf<DetailState>(DetailState.Loading) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(feedId, attempt) {
        state = DetailState.Loading
        state = try {
            val response = api.getFeedDetail(feedId)
            val data = response.data
            DetailState.Loaded(
                title = data.stringOrNull("title"),
                body = data.stringOrNull("message"),
            )
        } catch (e: Exception) {
            DetailState.Error(e.message ?: "加载失败，请重试")
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("动态 $feedId") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when (val current = state) {
                DetailState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is DetailState.Error -> {
                    Column {
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { attempt++ }) {
                            Text("重试")
                        }
                    }
                }
                is DetailState.Loaded -> {
                    Column {
                        current.title?.takeIf { it.isNotEmpty() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        current.body?.takeIf { it.isNotEmpty() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (current.title.isNullOrEmpty() &&
                            current.body.isNullOrEmpty()
                        ) {
                            Text(
                                text = "详情接口已接通，完整渲染（作者、图片、评论）是下一步。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
