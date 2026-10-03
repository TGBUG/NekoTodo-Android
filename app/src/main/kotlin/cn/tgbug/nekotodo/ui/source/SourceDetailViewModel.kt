package cn.tgbug.nekotodo.ui.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.data.ApiException
import cn.tgbug.nekotodo.data.SourceInfoDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 源信息详情:看这次的原始输入、附带的图片与文档、以及拆出的条目;改内容会触发重新拆解。 */
class SourceDetailViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val sourceInfoId: Long = 0,
        val detail: SourceInfoDetail? = null,
        val content: String = "",
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val deleted: Boolean = false,
        val sessionExpired: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(sourceInfoId: Long) {
        if (_state.value.sourceInfoId == sourceInfoId && _state.value.detail != null) return
        _state.value = UiState(sourceInfoId = sourceInfoId, isLoading = true)
        viewModelScope.launch { refresh() }
    }

    fun onContentChange(value: String) = _state.update {
        it.copy(content = value, message = null)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** 图片地址需要带鉴权,Coil 用的就是那个带 Authorization 头的客户端。 */
    fun fileUrl(fileUuid: String): String = container.repository.fileUrl(fileUuid)

    /** 保存内容并触发重新拆解(只为尚无任务的条目补任务)。 */
    fun saveAndRecompose() {
        val current = _state.value
        if (current.isSaving) return
        _state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            try {
                container.repository.reDecompose(current.sourceInfoId, current.content)
                _state.update { it.copy(isSaving = false, message = "已保存,重新拆解中…") }
                refresh()
            } catch (e: ApiException) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        // 409 = 已有进行中的拆解,给出比原始错误更明确的提示。
                        message = if (e.status == 409) {
                            "该源信息已有进行中的拆解,请稍后再试"
                        } else {
                            e.message
                        },
                    )
                }
            }
        }
    }

    fun delete(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.repository.deleteSourceInfo(_state.value.sourceInfoId)
                _state.update { it.copy(deleted = true) }
                onResult(null)
            } catch (e: ApiException) {
                if (e.status == 401) _state.update { it.copy(sessionExpired = true) }
                onResult(e.message)
            }
        }
    }

    private suspend fun refresh() {
        try {
            val detail = container.repository.sourceInfo(_state.value.sourceInfoId)
            _state.update {
                it.copy(
                    detail = detail,
                    content = detail.content,
                    isLoading = false,
                    error = null,
                )
            }
        } catch (e: ApiException) {
            if (e.status == 401) {
                _state.update { it.copy(sessionExpired = true) }
            } else {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
