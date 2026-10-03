package cn.tgbug.nekotodo.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.tgbug.nekotodo.NekoTodoApp
import cn.tgbug.nekotodo.ui.account.AccountViewModel
import cn.tgbug.nekotodo.ui.ai.AiCreateViewModel
import cn.tgbug.nekotodo.ui.appearance.AppearanceViewModel
import cn.tgbug.nekotodo.ui.login.LoginViewModel
import cn.tgbug.nekotodo.ui.search.SearchViewModel
import cn.tgbug.nekotodo.ui.source.SourceDetailViewModel
import cn.tgbug.nekotodo.ui.tasks.TasksViewModel

/** 手写 DI 的接线处:ViewModel 的依赖统一从 [NekoTodoApp.container] 取。 */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer { LoginViewModel(nekotodoApp().container) }
        initializer { TasksViewModel(nekotodoApp().container) }
        initializer { AiCreateViewModel(nekotodoApp().container) }
        initializer { SearchViewModel(nekotodoApp().container) }
        initializer { AccountViewModel(nekotodoApp().container) }
        initializer { SourceDetailViewModel(nekotodoApp().container) }
        initializer { AppearanceViewModel(nekotodoApp().container) }
    }
}

private fun CreationExtras.nekotodoApp(): NekoTodoApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NekoTodoApp
