package app.awavenue.c001apk.compose.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.URLDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local account vault, mirroring the Flutter fork's accountVaultV1
 * shape (accounts list + activeUid) without copying its weakness:
 * Flutter keeps the vault in a plaintext Hive box; here it lives in
 * EncryptedSharedPreferences, with a plain fallback only if the
 * device's encrypted store is unavailable. Passwords are never
 * stored — only the uid/username/token cookies from a login.
 */
@Serializable
data class SavedAccount(
    val uid: String,
    val username: String,
    val token: String,
    val avatar: String = "",
) {
    val displayName: String
        get() = try {
            URLDecoder.decode(username, "UTF-8")
        } catch (_: Exception) {
            username
        }
}

@Serializable
data class AccountVault(
    val accounts: List<SavedAccount> = emptyList(),
    val activeUid: String? = null,
)

object AccountStore {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AccountVault())
    val state: StateFlow<AccountVault> = _state.asStateFlow()

    val current: SavedAccount?
        get() = _state.value.accounts.firstOrNull { it.uid == _state.value.activeUid }

    val isLoggedIn: Boolean
        get() = current != null

    fun init(context: Context) {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "account_vault",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            context.getSharedPreferences("account_vault_fallback", Context.MODE_PRIVATE)
        }
        val raw = prefs.getString("vault", null)
        _state.value = try {
            raw?.let { json.decodeFromString<AccountVault>(it) } ?: AccountVault()
        } catch (_: Exception) {
            AccountVault()
        }
    }

    private fun persist(vault: AccountVault) {
        _state.value = vault
        prefs.edit().putString("vault", json.encodeToString(vault)).apply()
    }

    fun saveLogin(uid: String, username: String, token: String) {
        if (!uid.matches(Regex("^\\d+$")) || token.isEmpty()) return
        val previous = _state.value.accounts.firstOrNull { it.uid == uid }
        val account = SavedAccount(
            uid = uid,
            username = username,
            token = token,
            avatar = previous?.avatar.orEmpty(),
        )
        val accounts = _state.value.accounts.filterNot { it.uid == uid } + account
        persist(AccountVault(accounts = accounts, activeUid = uid))
    }

    fun switchTo(uid: String): Boolean {
        val account = _state.value.accounts.firstOrNull { it.uid == uid } ?: return false
        if (account.token.isEmpty()) return false
        if (_state.value.activeUid == uid) return true
        persist(_state.value.copy(activeUid = uid))
        return true
    }

    fun useGuest() {
        persist(_state.value.copy(activeUid = null))
    }

    fun remove(uid: String) {
        val accounts = _state.value.accounts.filterNot { it.uid == uid }
        val activeUid = if (_state.value.activeUid == uid) null else _state.value.activeUid
        persist(AccountVault(accounts = accounts, activeUid = activeUid))
    }
}
