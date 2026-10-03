package cn.tgbug.nekotodo.data

import okhttp3.MultipartBody

/**
 * 应用访问后端的唯一入口。
 *
 * 存在的意义有两个:把所有请求统一收敛成 [ApiException](不这样做的话调用方会拿到
 * Retrofit/OkHttp 的原始异常),以及把会话状态(服务器地址 + token)的变更收在一处。
 */
class NekoTodoRepository(
    private val session: SessionState,
    private val provider: ApiProvider,
) {

    /** Coil 加载图片时要用同一个带鉴权的客户端。 */
    val okHttp get() = provider.okHttp

    fun fileUrl(fileUuid: String): String = provider.fileUrl(fileUuid)

    val isLoggedIn: Boolean get() = session.hasSession

    fun logout() {
        session.token = null
    }

    suspend fun login(serverUrl: String, username: String, password: String) {
        session.serverUrl = serverUrl
        session.token = null
        session.token = apiCall { provider.api().login(LoginBody(username, password)) }.token
    }

    suspend fun me(): UserInfo = apiCall { provider.api().me() }

    suspend fun preferences(): Preferences = apiCall { provider.api().preferences() }

    suspend fun savePreferences(customPromptTemplate: String?, timezone: String): Preferences =
        apiCall { provider.api().patchPreferences(preferencesBody(customPromptTemplate, timezone)) }

    suspend fun changePassword(oldPassword: String, newPassword: String) =
        apiCall { provider.api().changePassword(ChangePasswordBody(oldPassword, newPassword)) }

    suspend fun revokeAllTokens() = apiCall { provider.api().revokeAll() }

    suspend fun deleteAccount(password: String) =
        apiCall { provider.api().deleteAccount(PasswordBody(password)) }

    suspend fun tasks(): List<Task> = apiCall { provider.api().listTasks() }

    suspend fun createTask(
        description: String,
        details: String,
        deadline: String?,
        category: String?,
    ): Task = apiCall {
        provider.api().createTask(taskFormBody(description, details, deadline, category))
    }

    suspend fun updateTask(
        taskId: Long,
        description: String,
        details: String,
        deadline: String?,
        category: String?,
    ): Task = apiCall {
        provider.api().patchTask(taskId, taskFormBody(description, details, deadline, category))
    }

    suspend fun setTaskStatus(taskId: Long, status: TaskStatus): Task =
        apiCall { provider.api().patchTask(taskId, taskStatusBody(status)) }

    suspend fun deleteTask(taskId: Long) = apiCall { provider.api().deleteTask(taskId) }

    suspend fun moveTask(taskId: Long, toPosition: Int): Task =
        apiCall { provider.api().moveTask(taskId, MoveBody(toPosition)) }

    suspend fun sourceInfos(): List<SourceInfo> = apiCall { provider.api().listSourceInfos() }

    suspend fun sourceInfo(sourceInfoId: Long): SourceInfoDetail =
        apiCall { provider.api().getSourceInfo(sourceInfoId) }

    suspend fun submitSourceInfo(
        content: String?,
        files: List<MultipartBody.Part>,
    ): DecompositionAccepted = apiCall {
        val contentPart = content?.takeIf { it.isNotBlank() }
            ?.let { MultipartBody.Part.createFormData("content", it) }
        provider.api().createSourceInfo(contentPart, files)
    }

    /** 内容改动后触发重新拆解(只为尚无任务的条目补任务)。 */
    suspend fun reDecompose(sourceInfoId: Long, content: String): DecompositionAccepted =
        apiCall { provider.api().patchSourceInfo(sourceInfoId, sourceContentBody(content)) }

    suspend fun deleteSourceInfo(sourceInfoId: Long) =
        apiCall { provider.api().deleteSourceInfo(sourceInfoId) }

    suspend fun runs(): List<Run> = apiCall { provider.api().listRuns() }

    suspend fun run(runId: String): Run = apiCall { provider.api().getRun(runId) }
}
