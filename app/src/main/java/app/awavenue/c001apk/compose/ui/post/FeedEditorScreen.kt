package app.awavenue.c001apk.compose.ui.post

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.api.runWriteWithCaptchaV1
import app.awavenue.c001apk.compose.data.api.toWriteOutcome
import app.awavenue.c001apk.compose.data.session.AccountStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared post/reply editor, ported from the Flutter fork's
 * ReplyPage (post and reply share one editor there too): id == null
 * in Flutter means post; here the mode is explicit. Batch 1 is text
 * only — no image picker, no emote panel (those arrive with the
 * image-upload batch). The publish button enables only on
 * non-blank text, and the v1 image captcha dialog appears when the
 * server demands it.
 */
enum class EditorMode { POST, REPLY }

data class EditorState(
    val message: String = "",
    val onlySelf: Boolean = false,
    val forward: Boolean = false,
    val captchaBitmap: Bitmap? = null,
    val captchaInput: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val published: Boolean = false,
)

class FeedEditorViewModel(
    private val api: CoolapkApi,
    private val mode: EditorMode,
    private val targetType: String?,
    private val targetId: String?,
    private val targetTitle: String?,
    private val replyType: String,
) : ViewModel() {
    private val _state = MutableStateFlow(
        EditorState(
            message = if (mode == EditorMode.POST && targetType == "tag" &&
                !targetTitle.isNullOrEmpty()
            ) {
                "#$targetTitle# "
            } else {
                ""
            },
        ),
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var pendingCaptcha: CompletableDeferred<String?>? = null

    fun onMessageChange(value: String) {
        _state.update { it.copy(message = value.take(500)) }
    }

    fun onOnlySelfChange(value: Boolean) {
        _state.update { it.copy(onlySelf = value) }
    }

    fun onForwardChange(value: Boolean) {
        _state.update { it.copy(forward = value) }
    }

    fun onCaptchaInputChange(value: String) {
        _state.update { it.copy(captchaInput = value) }
    }

    fun confirmCaptcha() {
        pendingCaptcha?.complete(_state.value.captchaInput)
        pendingCaptcha = null
        _state.update { it.copy(captchaBitmap = null, captchaInput = "") }
    }

    fun cancelCaptcha() {
        pendingCaptcha?.complete(null)
        pendingCaptcha = null
        _state.update { it.copy(captchaBitmap = null, captchaInput = "") }
    }

    /** Fetches a fresh captcha image while the dialog stays open (tap-to-refresh, like the Flutter page). */
    fun refreshCaptchaImage() {
        viewModelScope.launch {
            try {
                val response = api.captchaImage(System.currentTimeMillis())
                val bytes = response.body()?.bytes() ?: return@launch
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@launch
                _state.update { it.copy(captchaBitmap = bitmap, captchaInput = "") }
            } catch (_: Exception) {
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting || current.message.isBlank()) return
        if (!AccountStore.isLoggedIn) {
            _state.update { it.copy(error = "请先登录") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            val outcome = runWriteWithCaptchaV1(
                api = api,
                attempt = {
                    if (mode == EditorMode.POST) {
                        api.createFeed(
                            type = if (targetType == "apk") "comment" else "feed",
                            targetType = targetType,
                            targetId = targetId,
                            message = current.message,
                            publishStatus = if (current.onlySelf) 1 else 0,
                            pic = null,
                        ).toWriteOutcome()
                    } else {
                        api.reply(
                            id = targetId.orEmpty(),
                            type = replyType,
                            message = current.message,
                            replyAndForward = if (current.forward) 1 else 0,
                            pic = null,
                        ).toWriteOutcome()
                    }
                },
                requestCaptcha = { bitmap ->
                    val deferred = CompletableDeferred<String?>()
                    pendingCaptcha = deferred
                    _state.update { it.copy(captchaBitmap = bitmap, captchaInput = "") }
                    deferred.await()
                },
            )
            _state.update {
                it.copy(
                    submitting = false,
                    error = if (outcome.ok) null else outcome.message.ifEmpty { "发布失败" },
                    published = outcome.ok,
                )
            }
        }
    }
}

class FeedEditorViewModelFactory(
    private val api: CoolapkApi,
    private val mode: EditorMode,
    private val targetType: String?,
    private val targetId: String?,
    private val targetTitle: String?,
    private val replyType: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FeedEditorViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return FeedEditorViewModel(api, mode, targetType, targetId, targetTitle, replyType) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedEditorScreen(
    api: CoolapkApi,
    mode: EditorMode,
    targetType: String?,
    targetId: String?,
    targetTitle: String?,
    replyType: String,
    replyTo: String?,
    onBack: () -> Unit,
    onPublished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FeedEditorViewModel = viewModel(
        factory = FeedEditorViewModelFactory(api, mode, targetType, targetId, targetTitle, replyType),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.published) {
        LaunchedEffect(Unit) { onPublished() }
    }

    val title = when (mode) {
        EditorMode.POST -> if (!targetTitle.isNullOrEmpty()) "发布于：$targetTitle" else "发布动态"
        EditorMode.REPLY -> if (!replyTo.isNullOrEmpty()) "回复：$replyTo" else "回复"
    }

    state.captchaBitmap?.let { bitmap ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCaptcha,
            title = { Text("请输入验证码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "验证码，点击刷新",
                        modifier = Modifier
                            .height(48.dp)
                            .clickable { viewModel.refreshCaptchaImage() },
                    )
                    OutlinedTextField(
                        value = state.captchaInput,
                        onValueChange = viewModel::onCaptchaInputChange,
                        label = { Text("验证码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCaptcha) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelCaptcha) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::onMessageChange,
                label = { Text(if (mode == EditorMode.POST) "发布动态" else "写下你的回复") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = if (mode == EditorMode.POST) state.onlySelf else state.forward,
                    onCheckedChange = {
                        if (mode == EditorMode.POST) viewModel.onOnlySelfChange(it)
                        else viewModel.onForwardChange(it)
                    },
                )
                Text(
                    text = if (mode == EditorMode.POST) "仅自己可见" else "回复并转发",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = viewModel::submit,
                enabled = state.message.isNotBlank() && !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.submitting) "发布中…" else "发布")
            }
        }
    }
}
