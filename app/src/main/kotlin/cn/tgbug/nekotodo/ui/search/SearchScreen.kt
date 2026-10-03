package cn.tgbug.nekotodo.ui.search

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import cn.tgbug.nekotodo.domain.TaskFilter
import cn.tgbug.nekotodo.domain.Time
import cn.tgbug.nekotodo.ui.tasks.TaskFormSheet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索任务") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                label = { Text("搜索描述或细节") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            FilterRow(
                categories = state.categoryNames,
                selectedCategory = state.category,
                selectedStatus = state.status,
                onCategory = viewModel::onCategoryChange,
                onStatus = viewModel::onStatusChange,
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "截止日期",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { pickingFrom = true }) {
                    Text(state.from?.toString() ?: "开始")
                }
                TextButton(onClick = { pickingTo = true }) {
                    Text(state.to?.toString() ?: "结束")
                }
                if (state.from != null || state.to != null) {
                    TextButton(onClick = {
                        viewModel.onFromChange(null)
                        viewModel.onToChange(null)
                    }) { Text("清除") }
                }
            }

            Spacer(Modifier.height(4.dp))
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::reload) { Text("重试") }
                    }
                }

                else -> {
                    Text(
                        text = "共 ${state.results.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (state.results.isEmpty()) {
                        Text(
                            text = "无匹配结果",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.results, key = { it.id }) { task ->
                                SearchResultRow(
                                    task = task,
                                    now = Instant.now(),
                                    zone = state.zone,
                                    onClick = { editingTask = task },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickingFrom) {
        DatePickerSheet(
            initial = state.from,
            onDismiss = { pickingFrom = false },
            onPicked = { viewModel.onFromChange(it); pickingFrom = false },
        )
    }
    if (pickingTo) {
        DatePickerSheet(
            initial = state.to,
            onDismiss = { pickingTo = false },
            onPicked = { viewModel.onToChange(it); pickingTo = false },
        )
    }

    editingTask?.let { task ->
        TaskFormSheet(
            editing = task,
            focusField = null,
            categorySuggestions = state.categoryNames,
            zone = state.zone,
            onDismiss = { editingTask = null },
            onSubmit = { description, details, deadlineIso, category, onResult ->
                viewModel.submitEdit(
                    taskId = task.id,
                    description = description,
                    details = details,
                    deadlineIso = deadlineIso,
                    category = category,
                ) { error ->
                    onResult(error)
                    if (error == null) editingTask = null
                }
            },
        )
    }
}

@Composable
private fun FilterRow(
    categories: List<String>,
    selectedCategory: String?,
    selectedStatus: TaskStatus?,
    onCategory: (String?) -> Unit,
    onStatus: (TaskStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedStatus == null,
            onClick = { onStatus(null) },
            label = { Text("全部") },
        )
        FilterChip(
            selected = selectedStatus == TaskStatus.INCOMPLETE,
            onClick = { onStatus(TaskStatus.INCOMPLETE) },
            label = { Text("未完成") },
        )
        FilterChip(
            selected = selectedStatus == TaskStatus.COMPLETED,
            onClick = { onStatus(TaskStatus.COMPLETED) },
            label = { Text("已完成") },
        )
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategory(null) },
            label = { Text("全部分类") },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategory(category) },
                label = { Text(category) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    // 选择器给的是所选日期在 UTC 的零点,取 UTC 侧日期才是用户点的那天。
                    onPicked(
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                    )
                }
                    ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun SearchResultRow(
    task: Task,
    now: Instant,
    zone: java.time.ZoneId,
    onClick: () -> Unit,
) {
    val overdue = TaskFilter.isOverdue(task, now)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = task.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (!task.category.isNullOrBlank()) append("${task.category} · ")
                    append(if (task.status == TaskStatus.COMPLETED) "已完成" else "未完成")
                    task.deadline?.let {
                        append(" · ")
                        append(Time.formatShort(Time.parseApiInstant(it), zone))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
