package cn.tgbug.nekotodo.ui.account

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.tgbug.nekotodo.ui.common.ConfirmDialog
import cn.tgbug.nekotodo.ui.common.ConfirmWithPasswordDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var preferencesError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var dangerError by remember { mutableStateOf<String?>(null) }
    var showRevokeConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedOut) {
        if (state.signedOut) onSignOut()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账户") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- 偏好设置 ----
            SectionTitle("偏好设置")
            OutlinedTextField(
                value = state.promptTemplate,
                onValueChange = viewModel::onPromptChange,
                label = { Text("提示词模板(留空使用内置模板)") },
                minLines = 3,
                maxLines = 6,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.timezone,
                onValueChange = viewModel::onTimezoneChange,
                label = { Text("时区") },
                placeholder = { Text("如 Asia/Shanghai") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                state.timezoneLooksInvalid -> Text(
                    text = "这个时区无法识别,展示时会退回 UTC。请写成 IANA 名称,例如 Asia/Shanghai。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                state.timezoneIsBlank -> Text(
                    text = "留空将保存为 UTC。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = {
                    preferencesError = null
                    viewModel.savePreferences { error -> preferencesError = error }
                }) { Text("保存偏好") }
            }
            InlineError(preferencesError)

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            // ---- 外观(只存本机) ----
            SectionTitle("外观")
            OutlinedButton(
                onClick = onOpenAppearance,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("主题、主色与背景") }
            Text(
                text = "外观设置只存在本机——后端没有存放它的地方,换设备需要重新设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            // ---- 修改密码 ----
            SectionTitle("修改密码")
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it; passwordError = null },
                label = { Text("原密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; passwordError = null },
                label = { Text("新密码(至少 8 位)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = {
                    when {
                        oldPassword.isEmpty() -> passwordError = "请输入原密码"
                        newPassword.length < 8 -> passwordError = "新密码至少 8 位"
                        else -> {
                            passwordError = null
                            viewModel.changePassword(oldPassword, newPassword) { error ->
                                passwordError = error
                            }
                        }
                    }
                }) { Text("修改密码") }
            }
            Text(
                text = "修改成功后所有已登录设备都会失效,需要重新登录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InlineError(passwordError)

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            // ---- 危险操作 ----
            SectionTitle("危险操作")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { dangerError = null; showRevokeConfirm = true },
                    modifier = Modifier.weight(1f),
                ) { Text("撤销全部 token") }
                OutlinedButton(
                    onClick = { dangerError = null; showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("注销账户", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = "撤销后所有设备都需要重新登录;注销会删除账户与全部数据,不可恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InlineError(dangerError)

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            TextButton(
                onClick = viewModel::signOutLocally,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("登出(仅退出本机)") }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRevokeConfirm) {
        ConfirmDialog(
            title = "撤销全部 token",
            message = "确定撤销?所有已登录的设备都需要重新登录。",
            confirmLabel = "撤销",
            onDismiss = { showRevokeConfirm = false },
            onConfirm = {
                showRevokeConfirm = false
                viewModel.revokeAllTokens { error -> dangerError = error }
            },
        )
    }

    if (showDeleteConfirm) {
        ConfirmWithPasswordDialog(
            title = "注销账户",
            message = "注销不可逆:任务、源信息与文件都会被删除。",
            confirmLabel = "注销",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { password ->
                showDeleteConfirm = false
                viewModel.deleteAccount(password) { error -> dangerError = error }
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
private fun InlineError(error: String?) {
    if (error == null) return
    Text(
        text = error,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
