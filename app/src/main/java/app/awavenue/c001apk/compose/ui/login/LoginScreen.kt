package app.awavenue.c001apk.compose.ui.login

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.compose.data.api.AccountApi
import app.awavenue.c001apk.compose.data.api.ApiClient
import app.awavenue.c001apk.compose.data.api.CaptchaProofStore
import app.awavenue.c001apk.compose.data.session.AccountStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Login page, ported from the Flutter fork's login page: account +
 * password + on-demand image captcha. The login button stays
 * disabled until the pre-login chain yields a requestHash. The
 * password is never stored; only the uid/username/token cookies
 * from a successful submit are persisted (encrypted).
 */
data class LoginState(
    val account: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val captchaVisible: Boolean = false,
    val captchaBitmap: Bitmap? = null,
    val captchaInput: String = "",
    val requestHash: String? = null,
    val loadingParams: Boolean = true,
    val submitting: Boolean = false,
    val message: String? = null,
    val loggedIn: Boolean = false,
    val openWebLogin: Boolean = false,
)

class LoginViewModel : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    init {
        AccountApi.clearLoginSession()
        viewModelScope.launch {
            val hash = AccountApi.fetchRequestHash()
            _state.update {
                it.copy(
                    requestHash = hash,
                    loadingParams = false,
                    message = if (hash == null) "登录页面缺少验证参数，请稍后重试" else null,
                )
            }
        }
    }

    fun onAccountChange(value: String) {
        _state.update { it.copy(account = value) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun onCaptchaChange(value: String) {
        _state.update { it.copy(captchaInput = value) }
    }

    fun onTogglePassword() {
        _state.update { it.copy(showPassword = !it.showPassword) }
    }

    fun onWebLoginConsumed() {
        _state.update { it.copy(openWebLogin = false) }
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            val bitmap = AccountApi.fetchCaptchaImage()
            _state.update {
                it.copy(
                    captchaVisible = true,
                    captchaBitmap = bitmap,
                    captchaInput = "",
                    message = if (bitmap == null) "无法获取验证码" else it.message,
                )
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val hash = current.requestHash ?: return
        if (current.account.isEmpty() || current.password.isEmpty()) {
            _state.update { it.copy(message = "账号或密码为空") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, message = null) }
            val result = AccountApi.login(
                requestHash = hash,
                account = current.account,
                password = current.password,
                captcha = current.captchaInput,
            )
            when (result) {
                is AccountApi.LoginResult.Success -> {
                    AccountStore.saveLogin(result.uid, result.username, result.token)
                    ApiClient.clearSessionCookies()
                    CaptchaProofStore.clear()
                    _state.update { it.copy(submitting = false, loggedIn = true) }
                }
                is AccountApi.LoginResult.Failure -> {
                    _state.update { it.copy(submitting = false, message = result.message) }
                    val needsCaptcha = result.message in listOf("图形验证码不能为空", "图形验证码错误") ||
                        (current.captchaVisible && result.message == "密码错误")
                    if (needsCaptcha) {
                        refreshCaptcha()
                    } else if (result.message.contains("人机") ||
                        result.messageStatus == "err_request_captcha_v2"
                    ) {
                        _state.update { it.copy(openWebLogin = true) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onWebLogin: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loggedIn) {
        LaunchedEffect(Unit) { onLoggedIn() }
    }
    if (state.openWebLogin) {
        LaunchedEffect(Unit) {
            viewModel.onWebLoginConsumed()
            onWebLogin()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onWebLogin) {
                        Text("网页登录")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loadingParams) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = "正在准备登录…",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            OutlinedTextField(
                value = state.account,
                onValueChange = viewModel::onAccountChange,
                label = { Text("账号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                trailingIcon = {
                    if (state.account.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onAccountChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (state.showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    IconButton(onClick = viewModel::onTogglePassword) {
                        Icon(
                            imageVector = if (state.showPassword) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = if (state.showPassword) "隐藏密码" else "显示密码",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.captchaVisible) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.captchaInput,
                        onValueChange = viewModel::onCaptchaChange,
                        label = { Text("验证码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                    state.captchaBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "验证码，点击刷新",
                            modifier = Modifier
                                .height(48.dp)
                                .clickable { viewModel.refreshCaptcha() },
                        )
                    }
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = viewModel::submit,
                enabled = state.requestHash != null && !state.submitting &&
                    state.account.isNotEmpty() && state.password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.submitting) "登录中…" else "登录")
            }
            Text(
                text = "遇到人机验证或无法直接登录时，请使用网页登录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
