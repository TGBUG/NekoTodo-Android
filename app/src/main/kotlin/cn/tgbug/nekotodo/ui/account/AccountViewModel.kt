package cn.tgbug.nekotodo.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.data.ApiException
import cn.tgbug.nekotodo.domain.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val promptTemplate: String = "",
        val timezone: String = "UTC",
        val isLoading: Boolean = true,
        val error: String? = null,
        val message: String? = null,
        /** 改密 / 撤销 token / 注销之后凭据都已失效,必须回登录页。 */
        val signedOut: Boolean = false,
    ) {
        /** 时区写错不会导致保存失败,但展示会退化成 UTC——这里提前提示。 */
        val timezoneLooksInvalid: Boolean
            get() = timezone.isNotBlank() && runCatching { java.time.ZoneId.of(timezone.trim()) }.isFailure

        val timezoneIsBlank: Boolean get() = timezone.isBlank()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val preferences = container.repository.preferences()
            _state.value = _state.value.copy(
                promptTemplate = preferences.customPromptTemplate.orEmpty(),
                timezone = preferences.timezone,
                isLoading = false,
                error = null,
            )
        } catch (e: ApiException) {
            if (e.status == 401) {
                _state.value = _state.value.copy(signedOut = true)
            } else {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onPromptChange(value: String) = _state.value.let {
        _state.value = it.copy(promptTemplate = value, message = null)
    }

    fun onTimezoneChange(value: String) = _state.value.let {
        _state.value = it.copy(timezone = value, message = null)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** 偏好保存失败时把错误交给表单就地显示,不丢用户已经输入的内容。 */
    fun savePreferences(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val saved = container.repository.savePreferences(
                    customPromptTemplate = _state.value.promptTemplate.takeIf { it.isNotBlank() },
                    timezone = _state.value.timezone.trim().ifEmpty { "UTC" },
                )
                _state.value = _state.value.copy(
                    promptTemplate = saved.customPromptTemplate.orEmpty(),
                    timezone = saved.timezone,
                    message = "偏好已保存",
                )
                onResult(null)
            } catch (e: ApiException) {
                onResult(e.message)
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.repository.changePassword(oldPassword, newPassword)
                // 后端改密后会撤销全部 token,只能重新登录。
                _state.value = _state.value.copy(signedOut = true)
                onResult(null)
            } catch (e: ApiException) {
                onResult(e.message)
            }
        }
    }

    fun revokeAllTokens(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.repository.revokeAllTokens()
                _state.value = _state.value.copy(signedOut = true)
                onResult(null)
            } catch (e: ApiException) {
                onResult(e.message)
            }
        }
    }

    fun deleteAccount(password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.repository.deleteAccount(password)
                _state.value = _state.value.copy(signedOut = true)
                onResult(null)
            } catch (e: ApiException) {
                onResult(e.message)
            }
        }
    }

    /** 仅本地丢弃 token,不向后端撤销。 */
    fun signOutLocally() {
        viewModelScope.launch {
            container.logout()
            _state.value = _state.value.copy(signedOut = true)
        }
    }

    /** 展示用:时区是否可解析(与 Time.zone 的回退规则一致)。 */
    fun timezonePreview(): String = Time.zone(_state.value.timezone).id
}
