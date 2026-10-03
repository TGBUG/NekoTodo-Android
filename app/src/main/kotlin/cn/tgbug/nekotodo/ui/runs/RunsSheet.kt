package cn.tgbug.nekotodo.ui.runs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.tgbug.nekotodo.data.Run
import cn.tgbug.nekotodo.data.RunStatus
import cn.tgbug.nekotodo.domain.Time
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunsSheet(
    runs: List<Run>,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onOpenSource: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "拆解任务进度",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (runs.isEmpty()) {
                Text(
                    text = "暂无拆解记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(runs.take(MAX_RUNS), key = { it.id }) { run ->
                        RunRow(run = run, zone = zone, onClick = { onOpenSource(run.sourceInfoId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RunRow(run: Run, zone: ZoneId, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${run.id.take(8)} · 源信息 #${run.sourceInfoId}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = statusLabel(run.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(run.status),
                )
            }
            Spacer(Modifier.height(4.dp))
            val created = run.createdAt.takeIf { it.isNotBlank() }
                ?.let { runCatching { Time.formatShort(Time.parseApiInstant(it), zone) }.getOrNull() }
            Text(
                text = buildString {
                    if (created != null) append(created)
                    if (run.status == RunStatus.COMPLETED) {
                        if (isNotEmpty()) append(" · ")
                        append("新建 ${run.createdTaskIds.size} 个任务")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!run.error.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = run.error.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun statusLabel(status: RunStatus): String = when (status) {
    RunStatus.PENDING -> "等待中"
    RunStatus.RUNNING -> "拆解中"
    RunStatus.COMPLETED -> "已完成"
    RunStatus.FAILED -> "失败"
}

@Composable
private fun statusColor(status: RunStatus): Color = when (status) {
    RunStatus.PENDING, RunStatus.RUNNING -> MaterialTheme.colorScheme.primary
    RunStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    RunStatus.FAILED -> MaterialTheme.colorScheme.error
}

/** 后端按创建时间倒序返回,这里只展示最近若干条。 */
private const val MAX_RUNS = 30
