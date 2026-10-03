package cn.tgbug.nekotodo.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import cn.tgbug.nekotodo.domain.CategoryCount
import cn.tgbug.nekotodo.domain.TaskFilter
import cn.tgbug.nekotodo.domain.Time
import cn.tgbug.nekotodo.ui.common.ActionRow
import cn.tgbug.nekotodo.ui.common.ConfirmDialog
import cn.tgbug.nekotodo.ui.runs.RunsSheet
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Instant
import java.time.ZoneId

private data class FormRequest(val task: Task?, val focus: TaskFormField? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TasksViewModel,
    onSignOut: () -> Unit,
    onOpenAiCreate: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSource: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var formRequest by remember { mutableStateOf<FormRequest?>(null) }
    var menuTask by remember { mutableStateOf<Task?>(null) }
    var deleteTarget by remember { mutableStateOf<Task?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showRuns by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setPollingActive(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setPollingActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPollingActive(false)
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSignOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NekoTodo") },
                actions = {
                    TextButton(onClick = onOpenSearch) { Text("搜索") }
                    TextButton(onClick = { showRuns = true }) {
                        Text("进度")
                        if (state.hasActiveRun) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                            )
                        }
                    }
                    // 登出移到了账户页(与"撤销全部 token"等账户操作放在一起)。
                    TextButton(onClick = onOpenAccount) { Text("账户") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddMenu = true },
                // 默认底色是 primaryContainer,而主题把它设成了半透明——
                // 那样 FAB 的高度阴影会从容器里透出来,看着像渲染错误。这里用不透明主色。
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            CategoryBar(
                categories = state.categories,
                selected = state.selectedCategory,
                totalIncomplete = state.totalIncomplete,
                onSelect = viewModel::selectCategory,
            )

            when {
                state.isLoading -> CenteredContent { CircularProgressIndicator() }

                state.error != null -> CenteredContent {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::refreshNow) { Text("重试") }
                    }
                }

                else -> TaskList(
                    state = state,
                    viewModel = viewModel,
                    onToggle = viewModel::toggleStatus,
                    onToggleCollapsed = viewModel::toggleCompletedCollapsed,
                    onOpenMenu = { menuTask = it },
                )
            }
        }

        // 对标 Web 端左下角那个"源映射模式"浮动按钮。
        Surface(
            onClick = onOpenMap,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(
                text = "源映射模式",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
      }
    }

    formRequest?.let { request ->
        TaskFormSheet(
            editing = request.task,
            focusField = request.focus,
            categorySuggestions = state.categories.map { it.name },
            zone = state.zone,
            onDismiss = { formRequest = null },
            onSubmit = { description, details, deadlineIso, category, onResult ->
                viewModel.submitTaskForm(
                    taskId = request.task?.id,
                    description = description,
                    details = details,
                    deadlineIso = deadlineIso,
                    category = category,
                ) { error ->
                    onResult(error)
                    // 只在成功时收起,失败时把错误留在表单里,不丢用户填的内容。
                    if (error == null) formRequest = null
                }
            },
        )
    }

    menuTask?.let { task ->
        TaskActionsSheet(
            task = task,
            canMoveUp = viewModel.canMoveOffset(task.id, -1),
            canMoveDown = viewModel.canMoveOffset(task.id, 1),
            sourceInfoId = viewModel.sourceInfoIdFor(task.id),
            onDismiss = { menuTask = null },
            onEdit = { field ->
                menuTask = null
                formRequest = FormRequest(task, field)
            },
            onOpenSource = { sourceInfoId ->
                menuTask = null
                onOpenSource(sourceInfoId)
            },
            onMoveUp = {
                menuTask = null
                viewModel.moveByOffset(task.id, -1)
            },
            onMoveDown = {
                menuTask = null
                viewModel.moveByOffset(task.id, 1)
            },
            onDelete = {
                menuTask = null
                deleteTarget = task
            },
        )
    }

    deleteTarget?.let { task ->
        ConfirmDialog(
            title = "删除任务",
            message = "确定删除「${task.description}」?" +
                if (task.sourceItemId != null) {
                    "\n注意:若这是其源信息的最后一个任务,该源信息及其条目会被连带清理。"
                } else {
                    ""
                },
            confirmLabel = "删除",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                val target = task
                deleteTarget = null
                viewModel.deleteTask(target.id) { }
            },
        )
    }

    if (showAddMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAddMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                ActionRow("AI 创建") {
                    showAddMenu = false
                    onOpenAiCreate()
                }
                ActionRow("手动添加") {
                    showAddMenu = false
                    formRequest = FormRequest(null)
                }
            }
        }
    }

    if (showRuns) {
        RunsSheet(
            runs = state.runs,
            zone = state.zone,
            onDismiss = { showRuns = false },
            onOpenSource = { sourceInfoId ->
                showRuns = false
                onOpenSource(sourceInfoId)
            },
        )
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CategoryBar(
    categories: List<CategoryCount>,
    selected: String?,
    totalIncomplete: Int,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("全部 ($totalIncomplete)") },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selected == category.name,
                onClick = { onSelect(category.name) },
                label = { Text("${category.name} (${category.incomplete})") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskList(
    state: TasksViewModel.UiState,
    viewModel: TasksViewModel,
    onToggle: (Task) -> Unit,
    onToggleCollapsed: () -> Unit,
    onOpenMenu: (Task) -> Unit,
) {
    // 截止时间按用户配置的时区解释,而不是设备时区——与 Web 端一致。
    val now = Instant.now()
    val zone = state.zone
    val lazyListState = rememberLazyListState()

    // onMove 只在同一个截止日组内真正换位;跨组直接忽略,于是拖动到组边界就会被"挡住"。
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val draggedId = from.key as? Long ?: return@rememberReorderableLazyListState
        val targetId = to.key as? Long ?: return@rememberReorderableLazyListState
        viewModel.previewReorder(draggedId, targetId)
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title-incomplete") {
            SectionTitle("未完成 (${state.incomplete.size})")
        }
        if (state.incomplete.isEmpty()) {
            item(key = "empty-incomplete") { EmptyHint("没有未完成的任务,点右下角 + 新建") }
        }
        items(state.incomplete, key = { it.id }) { task ->
            ReorderableItem(reorderState, task.id) { isDragging ->
                TaskRow(
                    task = task,
                    now = now,
                    zone = zone,
                    onToggle = { onToggle(task) },
                    onOpenMenu = { onOpenMenu(task) },
                    modifier = Modifier
                        // longPressDraggableHandle 是 ReorderableItem 内容 lambda 的
                        // ReorderableCollectionItemScope 上的成员,长按整张卡片开始拖动。
                        .longPressDraggableHandle(
                            onDragStarted = { viewModel.beginReorder() },
                            onDragStopped = { viewModel.commitReorder(task.id) },
                        )
                        .alpha(if (isDragging) 0.85f else 1f),
                )
            }
        }

        item(key = "divider-completed") {
            CompletedDivider(
                count = state.completed.size,
                collapsed = state.completedCollapsed,
                onToggle = onToggleCollapsed,
            )
        }
        if (!state.completedCollapsed) {
            if (state.completed.isEmpty()) {
                item(key = "empty-completed") { EmptyHint("暂无已完成任务") }
            }
            items(state.completed, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    now = now,
                    zone = zone,
                    onToggle = { onToggle(task) },
                    onOpenMenu = { onOpenMenu(task) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
    )
}

@Composable
private fun CompletedDivider(count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = "已完成 ($count) ${if (collapsed) "▸" else "▾"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun TaskRow(
    task: Task,
    now: Instant,
    zone: ZoneId,
    onToggle: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = task.status == TaskStatus.COMPLETED
    val overdue = TaskFilter.isOverdue(task, now)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (task.details.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!task.category.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = task.category.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val deadline = task.deadline
                if (deadline != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = Time.formatShort(Time.parseApiInstant(deadline), zone) +
                            if (overdue) " 已过期" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            IconButton(onClick = onOpenMenu) {
                Text("⋮", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
