package cn.tgbug.nekotodo.ui.source

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.tgbug.nekotodo.data.FileKind
import cn.tgbug.nekotodo.data.SourceFile
import cn.tgbug.nekotodo.ui.common.ConfirmDialog
import coil3.compose.AsyncImage

/**
 * 源信息详情。
 *
 * 说明:Web 端这个页面只能从星图进入,而星图推迟到 v1.1,所以在 Android 上它是
 * 从「任务 ⋮ → 查看来源」和「进度面板 → 点某条拆解记录」进入的——这两个入口是
 * Web 端没有的,属于为了让溯源能力在没有星图时依然可用而新增的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailScreen(
    sourceInfoId: Long,
    viewModel: SourceDetailViewModel,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceInfoId) { viewModel.load(sourceInfoId) }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("源信息 #$sourceInfoId") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                val detail = state.detail
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = state.content,
                        onValueChange = viewModel::onContentChange,
                        label = { Text("内容(保存后会重新拆解)") },
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = viewModel::saveAndRecompose,
                            enabled = !state.isSaving,
                        ) { Text("保存并重新拆解") }
                    }
                    Text(
                        text = "重新拆解只为尚无任务的条目补任务,不会改动已有任务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (detail != null && detail.sourceFiles.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        SectionTitle("文件 (${detail.sourceFiles.size})")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(detail.sourceFiles, key = { it.id }) { file ->
                                FileCard(file = file, url = viewModel.fileUrl(file.fileUuid))
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SectionTitle("条目 (${detail?.sourceItems?.size ?: 0})")
                    if (detail?.sourceItems.isNullOrEmpty()) {
                        Text(
                            text = "暂无条目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        detail?.sourceItems?.forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }

                    val taskCount = detail?.tasks?.size ?: 0
                    Text(
                        text = "该源信息已拆出 $taskCount 个任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    OutlinedButton(
                        onClick = { deleteError = null; showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("删除源信息", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        text = "删除会级联清掉它的条目、拆出的全部任务与拆解记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (deleteError != null) {
                        Text(
                            text = deleteError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除源信息",
            message = "确定删除?其条目、拆出的全部任务与拆解记录都会一并删除。",
            confirmLabel = "删除",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete { error -> deleteError = error }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FileCard(file: SourceFile, url: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (file.kind == FileKind.IMAGE) {
                // 图片接口需要鉴权:这里用带 Authorization 头的 ImageLoader。
                AsyncImage(
                    model = url,
                    contentDescription = file.description.ifBlank { file.filename.orEmpty() },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            } else {
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = file.filename?.substringAfterLast('.', "")?.uppercase().orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = file.filename ?: if (file.kind == FileKind.IMAGE) "图片" else "文档",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            if (file.hasText) {
                Text(
                    text = "${file.textChars} 字符",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (file.description.isNotBlank()) {
                Text(
                    text = file.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
