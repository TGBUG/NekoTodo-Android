package cn.tgbug.nekotodo.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// PATCH 接口对"字段缺失"与"字段为 null"的处理不同(后者表示清空),
// 而 kotlinx.serialization 无法在同一个数据类里同时表达这两种意图,
// 因此需要精确控制请求体的这几处改用 JsonObject 手工构造。

private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
    put(key, value?.let(::JsonPrimitive) ?: JsonNull)
}

/** 任务编辑表单提交的完整字段集。deadline / category 传 null 表示清空。 */
fun taskFormBody(
    description: String,
    details: String,
    deadline: String?,
    category: String?,
): JsonObject = buildJsonObject {
    put("description", JsonPrimitive(description))
    put("details", JsonPrimitive(details))
    putNullable("deadline", deadline)
    putNullable("category", category)
}

/** 只切换完成状态,不触碰其它字段(Web 端勾选框的行为)。 */
fun taskStatusBody(status: TaskStatus): JsonObject = buildJsonObject {
    put("status", JsonPrimitive(status.wire))
}

/** 偏好设置。custom_prompt_template 传 null 表示清空、回落到内置模板。 */
fun preferencesBody(customPromptTemplate: String?, timezone: String): JsonObject = buildJsonObject {
    putNullable("custom_prompt_template", customPromptTemplate)
    put("timezone", JsonPrimitive(timezone))
}

fun sourceContentBody(content: String): JsonObject = buildJsonObject {
    put("content", JsonPrimitive(content))
}
