package cn.tgbug.nekotodo.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.tgbug.nekotodo.AppContainer
import cn.tgbug.nekotodo.data.ApiException
import cn.tgbug.nekotodo.data.Run
import cn.tgbug.nekotodo.data.RunStatus
import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import cn.tgbug.nekotodo.data.submitTaskForm
import cn.tgbug.nekotodo.domain.CategoryCount
import cn.tgbug.nekotodo.domain.TaskGrouping
import cn.tgbug.nekotodo.domain.TaskOrdering
import cn.tgbug.nekotodo.domain.Time
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId

class TasksViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val incomplete: List<Task> = emptyList(),
        val completed: List<Task> = emptyList(),
        val categories: List<CategoryCount> = emptyList(),
        val selectedCategory: String? = null,
        val totalIncomplete: Int = 0,
        val runs: List<Run> = emptyList(),
        val completedCollapsed: Boolean = false,
        val isLoading: Boolean = true,
        val hasActiveRun: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val sessionExpired: Boolean = false,
        val zone: ZoneId = ZoneId.of("UTC"),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var allTasks: List<Task> = emptyList()

    private var lastRuns: List<Run> = emptyList()

    private var zone: ZoneId = ZoneId.of("UTC")

    private var previousRunStatus: Map<String, RunStatus> = emptyMap()

    private var pollingJob: Job? = null

    /** 拖动过程中为 true:此时不要用服务端顺序覆盖本地顺序。 */
    private var reordering = false

    /** 本次拖动期间是否撞到过截止日组边界(松手时据此解释"为什么没动")。 */
    private var blockedDuringDrag = false

    init {
        viewModelScope.launch { loadTimezone() }
    }

    private suspend fun loadTimezone() {
        // 偏好读不到不致命:退化为 UTC 照常可用,用户可去账户页修正。
        runCatching { container.repository.preferences() }
            .onSuccess {
                zone = Time.zone(it.timezone)
                rebuild()
            }
    }

    /**
     * 屏幕可见时持续轮询,不可见时停止(与 Web 端一致,不做后台轮询)。
     *
     * 启停刻意做成幂等:重复的 ON_RESUME 不会重启循环。
     * 早先用 `repeatOnLifecycle` 包住一个死循环,该 API 每次进入 RESUMED 都会重启代码块,
     * 一旦 RESUMED 被反复触发就会变成"重启—取消在途请求—立刻重发"的风暴,
     * delay 永远等不到执行。
     */
    fun setPollingActive(active: Boolean) {
        if (active) {
            if (pollingJob?.isActive == true) return
            pollingJob = viewModelScope.launch {
                while (currentCoroutineContext().isActive) {
                    refresh()
                    delay(if (_state.value.hasActiveRun) ACTIVE_POLL_MS else IDLE_POLL_MS)
                }
            }
        } else {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    fun selectCategory(category: String?) = rebuild(selectedCategory = category)

    fun toggleCompletedCollapsed() = rebuild(completedCollapsed = !_state.value.completedCollapsed)

    fun consumeMessage() = rebuild(message = null)

    fun toggleStatus(task: Task) {
        val next = if (task.status == TaskStatus.COMPLETED) TaskStatus.INCOMPLETE else TaskStatus.COMPLETED
        viewModelScope.launch {
            try {
                container.repository.setTaskStatus(task.id, next)
                refresh()
            } catch (e: ApiException) {
                fail(e)
            }
        }
    }

    // ---- 拖动排序 ----

    fun beginReorder() {
        reordering = true
        blockedDuringDrag = false
    }

    /**
     * 拖动过程中的即时重排:只改界面顺序,不发请求。
     * 与目标不在同一截止日组时直接忽略——表现为"物理挡住",拖不过去。
     */
    fun previewReorder(draggedId: Long, targetId: Long) {
        val current = _state.value.incomplete
        val from = current.indexOfFirst { it.id == draggedId }
        val to = current.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return
        if (!TaskOrdering.sameDayGroup(current[from], current[to], zone)) {
            // 记下来,松手时告诉用户为什么没动;拖动途中不提示,否则会刷屏。
            blockedDuringDrag = true
            return
        }
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        _state.value = _state.value.copy(incomplete = reordered)
    }

    /** 松手:按本地顺序算出组内位次再提交给后端。 */
    fun commitReorder(draggedId: Long) {
        reordering = false
        val wasBlocked = blockedDuringDrag
        blockedDuringDrag = false

        val position = TaskOrdering.positionAfterDrop(
            tasks = allTasks,
            visibleOrder = _state.value.incomplete,
            draggedId = draggedId,
            zone = zone,
        )
        val currentPosition = TaskOrdering.positionOf(allTasks, draggedId, zone)
        if (position == null || position == currentPosition) {
            // 没有真正换位(本组只有它自己,或拖到组边界被挡住)。
            // 后者要给用户一个解释,否则会以为拖拽坏了。
            if (wasBlocked) {
                rebuild(message = "只能在同一截止日内调整顺序")
            } else if (position == null) {
                viewModelScope.launch { refresh() }
            }
            return
        }
        viewModelScope.launch { move(draggedId, position) }
    }

    /** 操作菜单里的上移/下移,同时也是无障碍下的排序兜底。 */
    fun moveByOffset(taskId: Long, offset: Int) {
        val position = TaskOrdering.positionByOffset(allTasks, taskId, offset, zone)
        if (position == null) {
            rebuild(message = if (offset < 0) "已经在最前面了" else "已经在最后面了")
            return
        }
        viewModelScope.launch { move(taskId, position) }
    }

    // ---- 增删改 ----

    fun deleteTask(taskId: Long, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                container.repository.deleteTask(taskId)
                refresh()
                onResult(null)
            } catch (e: ApiException) {
                if (e.status == 401) rebuild(sessionExpired = true)
                onResult(e.message)
            }
        }
    }

    /**
     * 手动建 / 改任务。回调收到 null 才算成功——表单据此决定是收起还是把错误显示在自己身上,
     * 这样提交失败不会把用户已经填好的内容丢掉。
     */
    fun submitTaskForm(
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
                refresh()
                onResult(null)
            } else {
                if (failure.status == 401) rebuild(sessionExpired = true)
                onResult(failure.message)
            }
        }
    }

    /** 供表单做输入建议的已有分类。 */
    fun knownCategories(): List<String> = _state.value.categories.map { it.name }

    /** 能否再上移/下移(到组边界就不能了),菜单用它决定是否置灰。 */
    fun canMoveOffset(taskId: Long, offset: Int): Boolean =
        TaskOrdering.positionByOffset(allTasks, taskId, offset, zone) != null

    /**
     * 由任务反查它所属的源信息。
     *
     * 任务只带 source_item_id,后端没有"由条目反查源信息"的接口,所以借拆解记录做映射
     * (run 里同时有 source_info_id 与 created_task_ids)。拆解记录会被按保留窗口清理,
     * 清理后就查不到了——返回 null 时界面不显示「查看来源」。
     */
    fun sourceInfoIdFor(taskId: Long): Long? =
        lastRuns.firstOrNull { taskId in it.createdTaskIds }?.sourceInfoId

    private suspend fun move(taskId: Long, position: Int) {
        try {
            container.repository.moveTask(taskId, position)
            refresh()
        } catch (e: ApiException) {
            fail(e)
        }
    }

    private suspend fun refresh() {
        try {
            val (tasks, runs) = coroutineScope {
                val tasksRequest = async { container.repository.tasks() }
                val runsRequest = async { container.repository.runs() }
                tasksRequest.await() to runsRequest.await()
            }
            allTasks = tasks
            lastRuns = runs
            val activeRun = runs.any { it.isActive }
            val message = completionMessage(runs)
            if (reordering) {
                // 正处于拖动过程中:只更新运行状态,不要用服务端顺序盖掉用户刚拖出来的顺序。
                _state.value = _state.value.copy(
                    hasActiveRun = activeRun,
                    isLoading = false,
                    error = null,
                    message = message,
                    runs = runs,
                )
            } else {
                rebuild(
                    hasActiveRun = activeRun,
                    isLoading = false,
                    error = null,
                    message = message,
                )
            }
        } catch (e: ApiException) {
            fail(e)
        }
    }

    /** 只在"上次还在跑、这次跑完/失败"时提示,首次加载不打扰。 */
    private fun completionMessage(runs: List<Run>): String? {
        var message: String? = null
        for (run in runs) {
            val previous = previousRunStatus[run.id]
            val wasActive = previous == RunStatus.PENDING || previous == RunStatus.RUNNING
            if (wasActive && run.status == RunStatus.COMPLETED) {
                message = "拆解完成:新建 ${run.createdTaskIds.size} 个任务"
            } else if (wasActive && run.status == RunStatus.FAILED) {
                message = "拆解失败:${run.error ?: "未知原因"}"
            }
            previousRunStatus = previousRunStatus + (run.id to run.status)
        }
        return message
    }

    private fun fail(e: ApiException) {
        if (e.status == 401) {
            rebuild(sessionExpired = true)
        } else {
            rebuild(isLoading = false, error = e.message)
        }
    }

    /** 所有状态变更都经过这里,保证派生列表不会与来源数据脱节。 */
    private fun rebuild(
        selectedCategory: String? = _state.value.selectedCategory,
        completedCollapsed: Boolean = _state.value.completedCollapsed,
        isLoading: Boolean = _state.value.isLoading,
        hasActiveRun: Boolean = _state.value.hasActiveRun,
        error: String? = _state.value.error,
        message: String? = _state.value.message,
        sessionExpired: Boolean = _state.value.sessionExpired,
    ) {
        val filtered = selectedCategory?.let { category -> allTasks.filter { it.category == category } }
            ?: allTasks
        val sorted = TaskOrdering.sorted(filtered, zone)
        val (incomplete, completed) = TaskGrouping.splitByCompletion(sorted)
        _state.value = UiState(
            incomplete = incomplete,
            completed = completed,
            categories = TaskGrouping.categories(allTasks),
            selectedCategory = selectedCategory,
            totalIncomplete = allTasks.count { it.status != TaskStatus.COMPLETED },
            runs = lastRuns,
            completedCollapsed = completedCollapsed,
            isLoading = isLoading,
            hasActiveRun = hasActiveRun,
            error = error,
            message = message,
            sessionExpired = sessionExpired,
            zone = zone,
        )
    }

    private companion object {
        const val ACTIVE_POLL_MS = 2_000L
        const val IDLE_POLL_MS = 10_000L
    }
}
