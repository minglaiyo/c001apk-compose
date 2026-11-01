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
import app.awavenue.c001apk.compose.data.repository.FeedRepository
import app.awavenue.c001apk.compose.ui.AppNavHost
import app.awavenue.c001apk.compose.ui.theme.C001apkComposeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
