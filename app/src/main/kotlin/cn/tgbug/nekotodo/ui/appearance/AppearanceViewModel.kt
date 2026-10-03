package cn.tgbug.nekotodo.ui.appearance

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.data.BackgroundImage
import cn.tgbug.nekotodo.domain.Appearance
import cn.tgbug.nekotodo.domain.BackgroundOption
import cn.tgbug.nekotodo.domain.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppearanceViewModel(private val container: AppContainer) : ViewModel() {

    val appearance: StateFlow<Appearance> = container.settings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Appearance())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setAccent(argb: Int) = update { it.copy(accent = argb) }

    fun setBackground(option: BackgroundOption) = update { it.copy(background = option) }

    /** 自定义背景要先降采样 + 模糊并落盘(SAF 权限会回收,不能只记 URI)。 */
    fun pickCustomBackground(uri: Uri) {
        viewModelScope.launch {
            _error.value = null
            try {
                val file = withContext(Dispatchers.IO) {
                    val prepared = BackgroundImage.prepare(container.appContext, uri)
                    // 新图有自己的文件名,旧的这张留着也没人引用,顺手清掉。
                    BackgroundImage.deletePrevious(container.appContext, prepared)
                    prepared
                }
                update {
                    it.copy(
                        background = BackgroundOption.CUSTOM,
                        customBackgroundPath = file.absolutePath,
                    )
                }
            } catch (e: Exception) {
                // 图片来源与格式都不可控,这里统一兜住并如实告诉用户。
                _error.value = "背景图处理失败:${e.message ?: "未知原因"}"
            }
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private fun update(transform: (Appearance) -> Appearance) {
        viewModelScope.launch {
            container.settings.saveAppearance(transform(appearance.value))
        }
    }
}
