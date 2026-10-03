package cn.tgbug.nekotodo.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.data.ApiException
import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import cn.tgbug.nekotodo.data.submitTaskForm
import cn.tgbug.nekotodo.domain.SearchQuery
import cn.tgbug.nekotodo.domain.TaskFilter
import cn.tgbug.nekotodo.domain.TaskGrouping
import cn.tgbug.nekotodo.domain.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 搜索全在客户端完成——后端没有搜索接口,所以这里一次取回全部任务再本地筛选。 */
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val text: String = "",
        val category: String? = null,
        val status: TaskStatus? = null,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
        val results: List<Task> = emptyList(),
        val categoryNames: List<String> = emptyList(),
        val zone: ZoneId = ZoneId.of("UTC"),
        val isLoading: Boolean = true,
        val error: String? = null,
        val sessionExpired: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var allTasks: List<Task> = emptyList()
    private var zone: ZoneId = ZoneId.of("UTC")

    init {
        viewModelScope.launch { load() }
    }

    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            // 时区读不到不致命,退化为 UTC。
            runCatching { container.repository.preferences() }
                .onSuccess { zone = Time.zone(it.timezone) }
            allTasks = container.repository.tasks()
            rebuild(isLoading = false, error = null)
        } catch (e: ApiException) {
            if (e.status == 401) {
                rebuild(sessionExpired = true)
            } else {
                rebuild(isLoading = false, error = e.message)
            }
        }
    }

    fun onTextChange(value: String) = apply(text = value)

    fun onCategoryChange(value: String?) = apply(category = value)

    fun onStatusChange(value: TaskStatus?) = apply(status = value)

    fun onFromChange(value: LocalDate?) = apply(from = value)

    fun onToChange(value: LocalDate?) = apply(to = value)

    /** 搜索页里改完任务后刷新结果。 */
    fun submitEdit(
        taskId: Long?,
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
                load()
                onResult(null)
            } else {
                if (failure.status == 401) rebuild(sessionExpired = true)
                onResult(failure.message)
            }
        }
    }

    private fun apply(
        text: String = _state.value.text,
        category: String? = _state.value.category,
        status: TaskStatus? = _state.value.status,
        from: LocalDate? = _state.value.from,
        to: LocalDate? = _state.value.to,
    ) {
        _state.value = _state.value.copy(
            text = text,
            category = category,
            status = status,
            from = from,
            to = to,
        )
        rebuild()
    }

    /** 所有筛选条件变化都经过这里,保证结果与条件不会脱节。 */
    private fun rebuild(
        isLoading: Boolean = _state.value.isLoading,
        error: String? = _state.value.error,
        sessionExpired: Boolean = _state.value.sessionExpired,
    ) {
        val current = _state.value
        val results = TaskFilter.apply(
            tasks = allTasks,
            query = SearchQuery(
                text = current.text,
                category = current.category,
                status = current.status,
                from = current.from,
                to = current.to,
            ),
            zone = zone,
        )
        _state.value = current.copy(
            results = results,
            categoryNames = TaskGrouping.categories(allTasks).map { it.name },
            zone = zone,
            isLoading = isLoading,
            error = error,
            sessionExpired = sessionExpired,
        )
    }
}
