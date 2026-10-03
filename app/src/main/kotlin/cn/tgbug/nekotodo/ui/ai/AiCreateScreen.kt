package cn.tgbug.nekotodo.ui.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.tgbug.nekotodo.data.Attachment
import cn.tgbug.nekotodo.data.createCaptureUri
import coil3.compose.AsyncImage

private const val MAX_PICK = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCreateScreen(
    viewModel: AiCreateViewModel,
    onSubmitted: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) viewModel.addImages(listOf(uri))
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICK),
    ) { uris -> viewModel.addImages(uris) }

    val pickDocuments = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.addDocuments(uris) }

    LaunchedEffect(state.submitted) {
        if (state.submitted) onSubmitted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 创建任务") },
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
                value = state.content,
                onValueChange = viewModel::onContentChange,
                label = { Text("粘贴作业清单 / 任务描述") },
                placeholder = { Text("要求(截止时间、分类等)直接写在内容里…") },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "附件 (${state.attachments.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        launchError = null
                        runCatching {
                            val uri = context.createCaptureUri()
                            pendingCaptureUri = uri
                            takePicture.launch(uri)
                        }.onFailure { launchError = "无法启动相机:${it.message ?: "未知原因"}" }
                    },
                    enabled = !state.isPreparing && !state.isSubmitting,
                ) { Text("拍照") }

                OutlinedButton(
                    onClick = {
                        launchError = null
                        runCatching {
                            pickImages.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }.onFailure { launchError = "无法打开相册:${it.message ?: "未知原因"}" }
                    },
                    enabled = !state.isPreparing && !state.isSubmitting,
                ) { Text("相册") }

                OutlinedButton(
                    onClick = {
                        launchError = null
                        runCatching {
                            // 用通配符,避免部分文件提供方把可选文件藏起来;
                            // 支持与否由客户端 SupportedFiles 判定并给出明确提示。
                            pickDocuments.launch(arrayOf("*/*"))
                        }.onFailure { launchError = "无法打开文件选择器:${it.message ?: "未知原因"}" }
                    },
                    enabled = !state.isPreparing && !state.isSubmitting,
                ) { Text("文件") }
            }

            if (state.attachments.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(state.attachments) { index, attachment ->
                        AttachmentCard(
                            attachment = attachment,
                            onRemove = { viewModel.removeAttachment(index) },
                        )
                    }
                }
            }

            if (state.isPreparing) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在压缩图片…", style = MaterialTheme.typography.bodySmall)
                }
            }

            val message = launchError ?: state.error
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting && !state.isPreparing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("提交给 AI 拆解")
                }
            }
        }
    }
}

@Composable
private fun AttachmentCard(attachment: Attachment, onRemove: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .width(96.dp)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                if (attachment.isImage) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = attachment.displayName.substringAfterLast('.', "").uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRemove) {
                Text("移除", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
