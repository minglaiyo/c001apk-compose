package app.awavenue.c001apk.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import app.awavenue.c001apk.compose.data.api.ApiClient
import app.awavenue.c001apk.compose.data.api.CaptchaProofStore
import app.awavenue.c001apk.compose.data.repository.FeedRepository
import app.awavenue.c001apk.compose.data.session.AccountStore
import app.awavenue.c001apk.compose.ui.AppNavHost
import app.awavenue.c001apk.compose.ui.theme.C001apkComposeTheme
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Images go through the app's OkHttp client, not Coil's
        // default: the image CDN rejects non-app User-Agents (567).
        // Coil 2.7 has no coil-okhttp artifact (OkHttp support lives
        // in coil-base), and the singleton is set via Coil.
        Coil.setImageLoader(ImageLoaderFactory {
            ImageLoader.Builder(applicationContext)
                .okHttpClient(ApiClient.okHttpClient)
                .build()
        })
        // Captcha proof store (SharedPreferences) for the validate=
        // cookie protocol; must be ready before any API request.
        CaptchaProofStore.init(applicationContext)
        // Account vault (EncryptedSharedPreferences); the API
        // cookie jar reads the active account from it on each request.
        AccountStore.init(applicationContext)
        // No DI framework in the skeleton: the graph is wired here.
        val api = ApiClient.create()
        val repository = FeedRepository(api)
        setContent {
            C001apkComposeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        repository = repository,
                        api = api,
                    )
                }
            }
        }
    }
}
