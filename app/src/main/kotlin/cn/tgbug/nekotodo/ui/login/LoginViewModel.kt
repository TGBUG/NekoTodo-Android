package cn.tgbug.nekotodo.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.data.ApiException
import cn.tgbug.nekotodo.data.ApiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val serverUrl: String = "",
        val username: String = "",
        val password: String = "",
        val serverHistory: List<String> = emptyList(),
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val loggedIn: Boolean = false,
    ) {
        val cleartextWarning: Boolean get() = ApiProvider.usesCleartext(serverUrl)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(serverUrl = container.settings.currentServerUrl()) }
        }
        viewModelScope.launch {
            container.settings.serverHistory.collect { history ->
                _state.update { it.copy(serverHistory = history) }
            }
        }
    }

    fun onServerUrlChange(value: String) =
        _state.update { it.copy(serverUrl = value, error = null) }

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, error = null) }

    /** 登出后不要把上一次的密码继续留在表单里。 */
    fun clearPassword() = _state.update { it.copy(password = "", error = null) }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val serverUrl = current.serverUrl.trim()
        val username = current.username.trim()
        val password = current.password

        val validationError = when {
            serverUrl.isEmpty() -> "请填写服务器地址"
            username.isEmpty() -> "请填写用户名"
            password.isEmpty() -> "请填写密码"
            else -> null
        }
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                container.login(serverUrl, username, password)
                _state.update { it.copy(isSubmitting = false, loggedIn = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }
}
