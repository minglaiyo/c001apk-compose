package app.awavenue.c001apk.compose.ui.login

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.awavenue.c001apk.compose.data.session.AccountStore

/**
 * Web login fallback, ported from the Flutter fork's webview page
 * with isLogin=1: prior account cookies are cleared first, the
 * account login page loads, and only the official top-level
 * landing page (https://www.coolapk.com/) can complete the login —
 * its cookies are read, saved, and the screen closes with success.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val saved = remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网页登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
                .padding(padding),
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        for (host in listOf("https://www.coolapk.com/", "https://account.coolapk.com/")) {
                            for (name in listOf("uid", "username", "token", "SESSID", "ddid")) {
                                cookieManager.setCookie(
                                    host,
                                    "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
                                )
                            }
                        }
                        cookieManager.flush()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (saved.value) return
                                val uri = url?.let { Uri.parse(it) } ?: return
                                if (uri.scheme != "https" || uri.host != "www.coolapk.com") return
                                if (!uri.path.isNullOrEmpty() && uri.path != "/") return
                                val raw = cookieManager.getCookie("https://www.coolapk.com/")
                                    .orEmpty()
                                val byName = raw.split(";").mapNotNull { part ->
                                    val kv = part.trim().split("=", limit = 2)
                                    if (kv.size == 2) kv[0] to kv[1] else null
                                }.toMap()
                                val uid = byName["uid"]
                                val username = byName["username"]
                                val token = byName["token"]
                                if (!uid.isNullOrEmpty() && !username.isNullOrEmpty() &&
                                    !token.isNullOrEmpty()
                                ) {
                                    saved.value = true
                                    AccountStore.saveLogin(uid, username, token)
                                    onLoggedIn()
                                }
                            }
                        }
                        loadUrl("https://account.coolapk.com/auth/login?type=mobile")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
