package cn.tgbug.nekotodo.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.domain.Time
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** 从操作菜单进入表单时,把光标直接落在对应的字段上(与 Web 端的行为一致)。 */
enum class TaskFormField { DESCRIPTION, DEADLINE, CATEGORY }

private const val WALL_PATTERN = "%04d-%02d-%02dT%02d:%02d"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormSheet(
    editing: Task?,
    focusField: TaskFormField?,
    categorySuggestions: List<String>,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onSubmit: (
        description: String,
        details: String,
        deadlineIso: String?,
        category: String?,
        onResult: (String?) -> Unit,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var description by remember { mutableStateOf(editing?.description.orEmpty()) }
    var details by remember { mutableStateOf(editing?.details.orEmpty()) }
    var category by remember { mutableStateOf(editing?.category.orEmpty()) }
    var deadlineWall by remember {
        mutableStateOf(
            editing?.deadline?.let { Time.instantToWall(Time.parseApiInstant(it), zone) },
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val descriptionFocus = remember { FocusRequester() }
    val deadlineFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }

    LaunchedEffect(focusField) {
        when (focusField) {
            TaskFormField.DESCRIPTION -> descriptionFocus.requestFocus()
            TaskFormField.DEADLINE -> deadlineFocus.requestFocus()
            TaskFormField.CATEGORY -> categoryFocus.requestFocus()
            null -> Unit
        }
    }

    fun applyPreset(instant: Instant) {
        deadlineWall = Time.instantToWall(instant, zone)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (editing == null) "创建任务" else "编辑任务",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it; error = null },
                label = { Text("任务描述") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(descriptionFocus),
            )

            Column {
                Text(
                    text = "截止日期",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val now = Instant.now()
                    AssistChip(onClick = { applyPreset(Time.tonight(now, zone)) }, label = { Text("今晚") })
                    AssistChip(
                        onClick = { applyPreset(Time.endOfDayAfter(now, zone, 1)) },
                        label = { Text("明天") },
                    )
                    AssistChip(
                        onClick = { applyPreset(Time.sameTimeAfter(now, zone, 3)) },
                        label = { Text("三天后") },
                    )
                    AssistChip(
                        onClick = { applyPreset(Time.sameTimeAfter(now, zone, 7)) },
                        label = { Text("一周后") },
                    )
                    AssistChip(onClick = { deadlineWall = null }, label = { Text("清除") })
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(deadlineFocus),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = deadlineWall
                            ?.let { runCatching { Time.formatShort(Time.wallToInstant(it, zone), zone) }.getOrNull() }
                            ?: "未设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showDatePicker = true }) { Text("选择日期时间") }
                }
            }

            OutlinedTextField(
                value = category,
                onValueChange = { category = it; error = null },
                label = { Text("分类(可留空)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    // 分类是要与后端做相等匹配的字符串,不能被输入法改写大小写。
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(categoryFocus),
            )
            if (categorySuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categorySuggestions.forEach { suggestion ->
                        AssistChip(
                            onClick = { category = suggestion; error = null },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("任务细节(可留空)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(
                    enabled = !submitting,
                    onClick = {
                        val trimmed = description.trim()
                        if (trimmed.isEmpty()) {
                            error = "描述不能为空"
                            return@Button
                        }
                        submitting = true
                        val deadlineIso = deadlineWall?.let {
                            runCatching { Time.wallToInstant(it, zone).toString() }.getOrNull()
                        }
                        onSubmit(
                            trimmed,
                            details,
                            deadlineIso,
                            category.trim().ifEmpty { null },
                        ) { result ->
                            submitting = false
                            error = result
                        }
                    },
                ) {
                    Text(if (editing == null) "创建" else "保存")
                }
            }
        }
    }

    if (showDatePicker) {
        val initial = deadlineWall
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?: LocalDateTime.now()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atZone(zone).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        // DatePicker 给的是所选日期在 UTC 的零点,取 UTC 侧日期才是用户选的那天。
                        pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        showTimePicker = true
                    }
                    showDatePicker = false
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val base = deadlineWall
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?: LocalDateTime.now()
        val timeState = rememberTimePickerState(
            initialHour = base.hour,
            initialMinute = base.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = pickedDate ?: LocalDate.now()
                    deadlineWall = WALL_PATTERN.format(
                        date.year,
                        date.monthValue,
                        date.dayOfMonth,
                        timeState.hour,
                        timeState.minute,
                    )
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
        )
    }
}
