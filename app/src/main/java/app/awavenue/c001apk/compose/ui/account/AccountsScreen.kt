package app.awavenue.c001apk.compose.ui.account

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awavenue.c001apk.compose.data.api.ApiClient
import app.awavenue.c001apk.compose.data.api.CaptchaProofStore
import app.awavenue.c001apk.compose.data.model.upgradedCoolapkUrl
import app.awavenue.c001apk.compose.data.session.AccountStore
import app.awavenue.c001apk.compose.data.session.SavedAccount
import coil.compose.AsyncImage

/**
 * Account management page, ported from the Flutter fork's accounts
 * page: saved accounts list (tap to switch, expired ones go back to
 * login), remove with a confirm dialog, add account, and guest
 * mode. There is no server-side logout: guest mode and removal are
 * local operations, and switching accounts clears the anonymous
 * session and the captcha proof, like the Flutter session bump.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val vault by AccountStore.state.collectAsStateWithLifecycle()
    var pendingRemove by remember { mutableStateOf<SavedAccount?>(null) }

    fun clearSession() {
        ApiClient.clearSessionCookies()
        CaptchaProofStore.clear()
    }

    pendingRemove?.let { account ->
        val isCurrent = account.uid == vault.activeUid
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("移除本机登录？") },
            text = {
                Text(
                    if (isCurrent) {
                        "当前账号将切换为游客。"
                    } else {
                        "将从本机移除该账号的登录信息。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AccountStore.remove(account.uid)
                        if (isCurrent) clearSession()
                        pendingRemove = null
                    },
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
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
            if (vault.accounts.isEmpty()) {
                Text(
                    text = "还没有登录的账号",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(vault.accounts, key = { it.uid }) { account ->
                        val isCurrent = account.uid == vault.activeUid
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when {
                                        isCurrent -> Unit
                                        account.token.isEmpty() -> onAddAccount()
                                        else -> {
                                            if (AccountStore.switchTo(account.uid)) {
                                                clearSession()
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            if (account.avatar.isNotEmpty()) {
                                AsyncImage(
                                    model = account.avatar.upgradedCoolapkUrl(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = account.displayName.ifEmpty { "UID ${account.uid}" },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = when {
                                        account.token.isEmpty() -> "登录已失效，点击重新登录"
                                        isCurrent -> "当前使用"
                                        else -> "UID ${account.uid}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { pendingRemove = account }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "移除",
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAddAccount,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加账号")
            }
            if (vault.activeUid != null) {
                OutlinedButton(
                    onClick = {
                        AccountStore.useGuest()
                        clearSession()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("使用游客模式")
                }
            }
        }
    }
}
