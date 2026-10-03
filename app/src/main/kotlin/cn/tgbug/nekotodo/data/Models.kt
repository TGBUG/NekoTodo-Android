package cn.tgbug.nekotodo.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus(val wire: String) {
    @SerialName("incomplete")
    INCOMPLETE("incomplete"),

    @SerialName("completed")
    COMPLETED("completed"),
}

@Serializable
data class Task(
    val id: Long,
    val description: String,
    val status: TaskStatus,
    val details: String = "",
    val deadline: String? = null,
    val priority: Int = 0,
    val category: String? = null,
    @SerialName("source_item_id") val sourceItemId: Long? = null,
)

@Serializable
data class SourceInfo(
    val id: Long,
    val content: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
enum class FileKind(val wire: String) {
    @SerialName("image")
    IMAGE("image"),

    @SerialName("document")
    DOCUMENT("document"),
}

@Serializable
data class SourceFile(
    val id: Long,
    @SerialName("source_info_id") val sourceInfoId: Long,
    val kind: FileKind,
    val filename: String? = null,
    val mime: String? = null,
    @SerialName("file_uuid") val fileUuid: String,
    val size: Long = 0,
    @SerialName("order_index") val orderIndex: Int = 0,
    val description: String = "",
    @SerialName("has_text") val hasText: Boolean = false,
    @SerialName("text_chars") val textChars: Int = 0,
)

@Serializable
data class SourceItem(
    val id: Long,
    @SerialName("source_info_id") val sourceInfoId: Long,
    val content: String,
    @SerialName("source_file_id") val sourceFileId: Long? = null,
)

@Serializable
data class SourceInfoDetail(
    val id: Long,
    val content: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("source_items") val sourceItems: List<SourceItem> = emptyList(),
    val tasks: List<Task> = emptyList(),
    @SerialName("source_files") val sourceFiles: List<SourceFile> = emptyList(),
)

@Serializable
enum class RunStatus(val wire: String) {
    @SerialName("pending")
    PENDING("pending"),

    @SerialName("running")
    RUNNING("running"),

    @SerialName("completed")
    COMPLETED("completed"),

    @SerialName("failed")
    FAILED("failed"),
}

@Serializable
data class Run(
    val id: String,
    @SerialName("source_info_id") val sourceInfoId: Long,
    val status: RunStatus,
    @SerialName("created_task_ids") val createdTaskIds: List<Long> = emptyList(),
    val error: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
) {
    val isActive: Boolean get() = status == RunStatus.PENDING || status == RunStatus.RUNNING
}

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class UserInfo(
    val id: Long,
    val username: String,
    @SerialName("custom_prompt_template") val customPromptTemplate: String? = null,
    val timezone: String = "UTC",
)

@Serializable
data class Preferences(
    @SerialName("custom_prompt_template") val customPromptTemplate: String? = null,
    val timezone: String = "UTC",
)

@Serializable
data class LoginBody(val username: String, val password: String)

@Serializable
data class ChangePasswordBody(
    @SerialName("old_password") val oldPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class PasswordBody(val password: String)

@Serializable
data class MoveBody(@SerialName("to_position") val toPosition: Int)

/** `POST /source-infos` 与 `PATCH /source-infos/{id}` 的返回:立即开始异步拆解。 */
@Serializable
data class DecompositionAccepted(
    @SerialName("source_info_id") val sourceInfoId: Long,
    @SerialName("run_id") val runId: String,
)
