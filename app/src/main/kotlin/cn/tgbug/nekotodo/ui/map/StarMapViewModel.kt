package cn.tgbug.nekotodo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.data.ApiException
import cn.tgbug.nekotodo.data.Run
import cn.tgbug.nekotodo.data.RunStatus
import cn.tgbug.nekotodo.data.SourceInfoDetail
import cn.tgbug.nekotodo.data.submitTaskForm
import cn.tgbug.nekotodo.domain.Time
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * 星图的数据来源。
 *
 * 与 Web 端的差别(有意为之):Web 每次轮询都把**每个**源信息的详情各拉一次
 * (N+1 个请求,2~10 秒一轮)。这里只有在"源信息集合变了"或"有拆解刚结束"时才重取详情,
 * 其余时候只拉便宜的列表与拆解记录。
 */
class StarMapViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val details: List<SourceInfoDetail> = emptyList(),
        val zone: ZoneId = ZoneId.of("UTC"),
        val isLoading: Boolean = true,
        val error: String? = null,
        val message: String? = null,
        val sessionExpired: Boolean = false,
    ) {
        /** 星图里的全部任务(点某个任务节点时要取出完整的 Task)。 */
        val allTasks get() = details.flatMap { it.tasks }

        val categoryNames: List<String>
            get() = allTasks.mapNotNull { it.category?.takeIf(String::isNotBlank) }.distinct().sorted()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var knownSourceIds: List<Long> = emptyList()
    private var previousRunStatus: Map<String, RunStatus> = emptyMap()
    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { container.repository.preferences() }
                .onSuccess { _state.update { state -> state.copy(zone = Time.zone(it.timezone)) } }
            refresh(force = true)
        }
    }

    /** 与列表页同一套幂等启停:重复的 ON_RESUME 不会把轮询重启成风暴。 */
    fun setPollingActive(active: Boolean) {
        if (active) {
            if (pollingJob?.isActive == true) return
            pollingJob = viewModelScope.launch {
                while (currentCoroutineContext().isActive) {
                    refresh(force = false)
                    delay(POLL_INTERVAL_MS)
                }
            }
        } else {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh(force = true) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** 图片节点要带鉴权取图,复用那个带 Authorization 头的客户端。 */
    fun fileUrl(fileUuid: String): String = container.repository.fileUrl(fileUuid)

    /** 星图里点任务节点后编辑,复用与搜索页相同的表单提交语义。 */
    fun submitEdit(
        taskId: Long,
        description: String,
        details: String,
        deadlineIso: String?,
        category: String?,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val failure = container.repository.submitTaskForm(
                taskId = taskId,
                description = description,
                details = details,
                deadlineIso = deadlineIso,
                category = category,
            )
            if (failure == null) {
                refresh(force = true)
                onResult(null)
            } else {
                if (failure.status == 401) {
                    _state.update { it.copy(sessionExpired = true) }
                }
                onResult(failure.message)
            }
        }
    }

    private suspend fun refresh(force: Boolean) {
        try {
            val (sources, runs) = coroutineScope {
                val sourcesRequest = async { container.repository.sourceInfos() }
                val runsRequest = async { container.repository.runs() }
                sourcesRequest.await() to runsRequest.await()
            }

            val ids = sources.map { it.id }
            val runJustFinished = runs.any { run ->
                val previous = previousRunStatus[run.id]
                (previous == RunStatus.PENDING || previous == RunStatus.RUNNING) &&
                    (run.status == RunStatus.COMPLETED || run.status == RunStatus.FAILED)
            }
            previousRunStatus = runs.associate { it.id to it.status }

            val needDetails = force ||
                ids != knownSourceIds ||
                _state.value.details.isEmpty() && ids.isNotEmpty() ||
                runJustFinished

            if (needDetails) {
                knownSourceIds = ids
                val loaded = coroutineScope {
                    ids.map { id ->
                        async { runCatching { container.repository.sourceInfo(id) }.getOrNull() }
                    }.awaitAll()
                }
                _state.update {
                    it.copy(details = loaded.filterNotNull(), isLoading = false, error = null)
                }
            } else {
                _state.update { it.copy(isLoading = false, error = null) }
            }
        } catch (e: ApiException) {
            if (e.status == 401) {
                _state.update { it.copy(sessionExpired = true) }
            } else {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 10_000L
    }
}
