package cn.tgbug.nekotodo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.ui.login.LoginScreen
import cn.tgbug.nekotodo.ui.login.LoginViewModel
import cn.tgbug.nekotodo.ui.tasks.TaskListScreen
import kotlinx.coroutines.launch

/**
 * 会话闸门:先读本地会话,再决定进登录页还是主界面。
 *
 * 这里刻意不用 Navigation Compose——"登录 / 未登录"不是一条前进后退的栈,
 * 用状态切换表达更直白;真正的页面跳转(搜索、账户、源信息详情)再交给导航库。
 */
@Composable
fun NekoTodoRoot(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf<Boolean?>(null) }

    // 提到这一层是为了让"登出"能顺手清掉表单里的旧密码。
    val loginViewModel: LoginViewModel = viewModel(factory = AppViewModelProvider.Factory)

    LaunchedEffect(Unit) {
        loggedIn = container.restoreSession()
    }

    val signOut: () -> Unit = {
        scope.launch {
            container.logout()
            loginViewModel.clearPassword()
            loggedIn = false
        }
    }

    when (loggedIn) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        true -> MainScreen(container = container, onSignOut = signOut)

        false -> LoginScreen(
            viewModel = loginViewModel,
            onLoggedIn = { loggedIn = true },
        )
    }
}
