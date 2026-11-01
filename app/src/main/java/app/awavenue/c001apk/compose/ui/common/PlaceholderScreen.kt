package app.awavenue.c001apk.compose.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.net.URLDecoder

/**
 * Temporary destination for card taps whose page is not built yet
 * (app detail, user home). Explicit about the gap instead of a
 * dead tap; removed once those pages land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    kind: String,
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val decodedLabel = try {
        URLDecoder.decode(label, "UTF-8")
    } catch (_: Exception) {
        label
    }
    val title = when (kind) {
        "apk" -> "应用详情"
        "user" -> "用户主页"
        else -> "详情"
    }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
            },
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = decodedLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "这个页面在后续阶段实现",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
