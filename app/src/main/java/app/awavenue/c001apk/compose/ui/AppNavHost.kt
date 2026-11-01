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
import app.awavenue.c001apk.compose.ui.common.PlaceholderScreen
import app.awavenue.c001apk.compose.ui.common.routeForCardUrl
import app.awavenue.c001apk.compose.ui.detail.FeedDetailScreen
import app.awavenue.c001apk.compose.ui.feed.FeedListScreen
import app.awavenue.c001apk.compose.ui.feed.FeedViewModelFactory
import app.awavenue.c001apk.compose.ui.product.ProductScreen
import app.awavenue.c001apk.compose.ui.search.SearchScreen
import app.awavenue.c001apk.compose.ui.topic.TopicScreen
import java.net.URLDecoder
import java.net.URLEncoder

/** Routes: feed list, detail, search, topic, product, placeholder. */
@Composable
fun AppNavHost(
    navController: NavHostController,
    repository: FeedRepository,
    api: CoolapkApi,
    modifier: Modifier = Modifier,
) {
    val onCardClick: (String) -> Unit = { url ->
        routeForCardUrl(url)?.let { navController.navigate(it) }
    }
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
                onSearchClick = {
                    navController.navigate("search")
                },
            )
        }
        composable(
            route = "search?pageType={pageType}&pageParam={pageParam}",
            arguments = listOf(
                navArgument("pageType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("pageParam") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val pageType = backStackEntry.arguments?.getString("pageType")
            val rawParam = backStackEntry.arguments?.getString("pageParam")
            val pageParam = rawParam?.let {
                try {
                    URLDecoder.decode(it, "UTF-8")
                } catch (_: Exception) {
                    it
                }
            }
            SearchScreen(
                api = api,
                onBack = { navController.popBackStack() },
                onFeedClick = { feedId ->
                    navController.navigate("feed/$feedId")
                },
                onCardClick = onCardClick,
                pageType = pageType,
                pageParam = pageParam,
            )
        }
        composable(
            route = "topic/{name}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val rawName = backStackEntry.arguments?.getString("name").orEmpty()
            val name = try {
                URLDecoder.decode(rawName, "UTF-8")
            } catch (_: Exception) {
                rawName
            }
            TopicScreen(
                api = api,
                topicName = name,
                onBack = { navController.popBackStack() },
                onFeedClick = { feedId ->
                    navController.navigate("feed/$feedId")
                },
                onCardClick = onCardClick,
                onSearchClick = { topic ->
                    navController.navigate(
                        "search?pageType=tag&pageParam=" + URLEncoder.encode(topic, "UTF-8"),
                    )
                },
            )
        }
        composable(
            route = "product/{id}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("id").orEmpty()
            ProductScreen(
                api = api,
                productId = productId,
                onBack = { navController.popBackStack() },
                onFeedClick = { feedId ->
                    navController.navigate("feed/$feedId")
                },
                onCardClick = onCardClick,
                onSearchClick = { id ->
                    navController.navigate(
                        "search?pageType=product_phone&pageParam=" + URLEncoder.encode(id, "UTF-8"),
                    )
                },
            )
        }
        composable(
            route = "placeholder/{kind}/{label}",
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("label") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            PlaceholderScreen(
                kind = backStackEntry.arguments?.getString("kind").orEmpty(),
                label = backStackEntry.arguments?.getString("label").orEmpty(),
                onBack = { navController.popBackStack() },
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
