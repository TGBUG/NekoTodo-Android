package cn.tgbug.nekotodo.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.ui.account.AccountScreen
import cn.tgbug.nekotodo.ui.ai.AiCreateScreen
import cn.tgbug.nekotodo.ui.appearance.AppearanceScreen
import cn.tgbug.nekotodo.ui.map.StarMapScreen
import cn.tgbug.nekotodo.ui.search.SearchScreen
import cn.tgbug.nekotodo.ui.source.SourceDetailScreen
import cn.tgbug.nekotodo.ui.tasks.TaskListScreen

private object Routes {
    const val TASKS = "tasks"
    const val AI_CREATE = "ai-create"
    const val SEARCH = "search"
    const val ACCOUNT = "account"
    const val APPEARANCE = "appearance"
    const val STAR_MAP = "star-map"
    const val SOURCE_DETAIL = "source"

    fun sourceDetail(sourceInfoId: Long): String = "$SOURCE_DETAIL/$sourceInfoId"
}

/**
 * 已登录后的主区域。
 *
 * 会话闸门(登录/未登录)是状态切换,不需要导航栈;进入之后就有真正的"页面"了
 * (AI 创建、之后的搜索/账户/源信息详情),这些需要返回栈与系统返回键,交给 Navigation Compose。
 * 每个页面里的 ViewModel 由 NavBackStackEntry 作为 owner,出栈即释放。
 */
@Composable
fun MainScreen(container: AppContainer, onSignOut: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.TASKS) {
        composable(Routes.TASKS) {
            TaskListScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onSignOut = onSignOut,
                onOpenAiCreate = { navController.navigate(Routes.AI_CREATE) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                onOpenSource = { id -> navController.navigate(Routes.sourceDetail(id)) },
                onOpenMap = { navController.navigate(Routes.STAR_MAP) },
            )
        }

        composable(Routes.AI_CREATE) {
            AiCreateScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onSubmitted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() },
                onSessionExpired = onSignOut,
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() },
                onSignOut = onSignOut,
                onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
            )
        }

        composable(Routes.STAR_MAP) {
            StarMapScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() },
                onOpenSource = { id -> navController.navigate(Routes.sourceDetail(id)) },
                onSessionExpired = onSignOut,
            )
        }

        composable(Routes.APPEARANCE) {
            AppearanceScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.SOURCE_DETAIL}/{sourceInfoId}",
            arguments = listOf(navArgument("sourceInfoId") { type = NavType.LongType }),
        ) { entry ->
            val sourceInfoId = entry.arguments?.getLong("sourceInfoId") ?: return@composable
            SourceDetailScreen(
                sourceInfoId = sourceInfoId,
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() },
                onSessionExpired = onSignOut,
            )
        }
    }
}
