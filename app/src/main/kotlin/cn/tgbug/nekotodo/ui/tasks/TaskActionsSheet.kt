package cn.tgbug.nekotodo.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.ui.common.ActionRow

/**
 * 任务操作菜单。
 *
 * Web 端是鼠标右键菜单;手机上长按已经被"拖拽排序"占用,所以改由每张卡片上的 ⋮ 触发,
 * 内容与 Web 一致:编辑内容 / 修改日期 / 修改分类 / 上移 / 下移 / 删除。
 * 上移下移同时也是无障碍场景下的排序兜底(TalkBack 无法拖拽)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskActionsSheet(
    task: Task,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    sourceInfoId: Long?,
    onDismiss: () -> Unit,
    onEdit: (TaskFormField) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenSource: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = task.description,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            ActionRow("编辑内容") { onEdit(TaskFormField.DESCRIPTION) }
            ActionRow("修改日期") { onEdit(TaskFormField.DEADLINE) }
            ActionRow("修改分类") { onEdit(TaskFormField.CATEGORY) }
            // 只在能反查出源信息时才出现(拆解记录被清理后就查不到了)。
            if (sourceInfoId != null) {
                ActionRow("查看来源") { onOpenSource(sourceInfoId) }
            }
            ActionRow("上移", enabled = canMoveUp, onClick = onMoveUp)
            ActionRow("下移", enabled = canMoveDown, onClick = onMoveDown)
            ActionRow("删除", color = MaterialTheme.colorScheme.error, onClick = onDelete)
        }
    }
}
