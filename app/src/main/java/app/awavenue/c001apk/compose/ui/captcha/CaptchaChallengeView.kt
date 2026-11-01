package app.awavenue.c001apk.compose.ui.captcha

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.compose.data.api.CaptchaChallenge
import app.awavenue.c001apk.compose.data.api.CaptchaProofStore

/**
 * Shared verification block for list screens (search results,
 * topic/product tab lists), mirroring the detail screen's pattern:
 * the server's gating notice plus a [去验证] button. On success the
 * proof is saved (24h, auto-attached by the cookie jar) and
 * [onVerified] reloads the list.
 */
@Composable
fun CaptchaChallengeView(
    challenge: CaptchaChallenge,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var verifying by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "当前访问需要验证码",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Button(onClick = { verifying = true }) {
            Text("去验证")
        }
    }
    if (verifying) {
        CaptchaWebView(
            challenge = challenge,
            onVerified = { validate ->
                challenge.proof(validate)?.let { CaptchaProofStore.save(it) }
                verifying = false
                onVerified()
            },
            onCancel = { verifying = false },
        )
    }
}
