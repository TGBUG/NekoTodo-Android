package cn.tgbug.nekotodo.ui.ai

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.data.ApiException
import cn.tgbug.nekotodo.data.Attachment
import cn.tgbug.nekotodo.data.PhotoCompressor
import cn.tgbug.nekotodo.data.attachmentFrom
import cn.tgbug.nekotodo.data.attachmentFromFile
import cn.tgbug.nekotodo.data.captureCacheDirectory
import cn.tgbug.nekotodo.data.toUploadPart
import cn.tgbug.nekotodo.domain.SupportedFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** AI 创建:提交源信息后由后端异步拆解,这里只负责攒输入并上传。 */
class AiCreateViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val content: String = "",
        val attachments: List<Attachment> = emptyList(),
        val isPreparing: Boolean = false,
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val submitted: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onContentChange(value: String) = _state.update { it.copy(content = value, error = null) }

    fun removeAttachment(index: Int) = _state.update {
        it.copy(attachments = it.attachments.filterIndexed { i, _ -> i != index })
    }

    /** 拍照与相册都走这里:先降采样重新编码,保证不撞 20MB 上限。 */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isPreparing = true, error = null) }
            val context = container.appContext
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val compressed = PhotoCompressor.compress(
                            context = context,
                            source = uri,
                            targetDirectory = context.captureCacheDirectory(),
                        )
                        context.attachmentFromFile(compressed)
                    }.onFailure {
                        // 静默跳过会让"照片没上传"变成难查的问题,所以留一条日志。
                        Log.w("AiCreate", "图片处理失败:$uri", it)
                    }.getOrNull()
                }
            }
            _state.update {
                it.copy(
                    isPreparing = false,
                    attachments = it.attachments + added,
                    error = if (added.size < uris.size) {
                        "有 ${uris.size - added.size} 张图片读取失败,已跳过"
                    } else {
                        it.error
                    },
                )
            }
        }
    }

    /** 文档:不支持的类型在这里就挡下,而不是等上传后由后端报错。 */
    fun addDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val context = container.appContext
            val inspected = withContext(Dispatchers.IO) { uris.map { context.attachmentFrom(it) } }
            val (supported, rejected) =
                inspected.partition { SupportedFiles.isSupported(it.displayName, it.mime) }
            _state.update {
                it.copy(
                    attachments = it.attachments + supported,
                    error = if (rejected.isEmpty()) {
                        null
                    } else {
                        "已跳过不支持的文件:" +
                            rejected.joinToString("、") { attachment -> attachment.displayName } +
                            "(支持 ${SupportedFiles.describe()})"
                    },
                )
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.content.isBlank() && current.attachments.isEmpty()) {
            _state.update { it.copy(error = "请输入内容或添加图片") }
            return
        }

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                val resolver = container.appContext.contentResolver
                val parts = current.attachments.map { it.toUploadPart(resolver) }
                container.repository.submitSourceInfo(
                    content = current.content.takeIf { it.isNotBlank() },
                    files = parts,
                )
                _state.update { it.copy(isSubmitting = false, submitted = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }
}
