package cn.tgbug.nekotodo.data

/**
 * 手动建/改任务表单的提交语义。列表页与搜索页都要用,所以收在一处,
 * 避免两处各写一遍分叉。
 *
 * 返回 null 表示成功;否则返回异常本身,由调用方决定(401 会话语义 vs 展示错误文本)。
 */
suspend fun NekoTodoRepository.submitTaskForm(
    taskId: Long?,
    description: String,
    details: String,
    deadlineIso: String?,
    category: String?,
): ApiException? = try {
    if (taskId == null) {
        createTask(description, details, deadlineIso, category)
    } else {
        updateTask(taskId, description, details, deadlineIso, category)
    }
    null
} catch (e: ApiException) {
    e
}
