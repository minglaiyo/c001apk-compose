package app.awavenue.c001apk.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.awavenue.c001apk.compose.data.api.CoolapkApi
import app.awavenue.c001apk.compose.data.repository.FeedRepository
import app.awavenue.c001apk.compose.ui.detail.FeedDetailScreen
import app.awavenue.c001apk.compose.ui.feed.FeedListScreen
import app.awavenue.c001apk.compose.ui.feed.FeedViewModelFactory

/** Two routes for the skeleton: the feed list and a detail placeholder. */
@Composable
fun AppNavHost(
    navController: NavHostController,
    repository: FeedRepository,
    api: CoolapkApi,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = "feed",
        modifier = modifier,
    ) {
        composable("feed") {
            FeedListScreen(
                viewModel = viewModel(factory = FeedViewModelFactory(repository)),
                onItemClick = { feedId ->
                    navController.navigate("feed/$feedId")
                },
            )
        }
        composable(
            route = "feed/{feedId}",
            arguments = listOf(
                navArgument("feedId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val feedId = backStackEntry.arguments?.getString("feedId").orEmpty()
            FeedDetailScreen(
                api = api,
                feedId = feedId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
