package cn.tgbug.nekotodo.domain

import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class SearchQuery(
    val text: String = "",
    val category: String? = null,
    val status: TaskStatus? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

object TaskFilter {

    fun isOverdue(task: Task, now: Instant): Boolean =
        task.status != TaskStatus.COMPLETED &&
            task.deadline != null &&
            Time.parseApiInstant(task.deadline) < now

    /**
     * 搜索在客户端完成(后端没有搜索接口)。
     * 日期区间按**用户时区**的自然日解释:from 从当天 00:00 起,to 到当天 23:59 止;
     * 一旦给了日期条件,无截止时间的任务一律排除。
     */
    fun apply(tasks: List<Task>, query: SearchQuery, zone: ZoneId): List<Task> {
        val needle = query.text.trim().lowercase()
        val fromInstant = query.from?.atTime(LocalTime.MIN)?.atZone(zone)?.toInstant()
        val toInstant = query.to?.atTime(LocalTime.of(23, 59))?.atZone(zone)?.toInstant()

        val matched = tasks.filter { task ->
            if (needle.isNotEmpty()) {
                val inDescription = task.description.lowercase().contains(needle)
                val inDetails = task.details.lowercase().contains(needle)
                if (!inDescription && !inDetails) return@filter false
            }
            if (query.category != null && task.category != query.category) return@filter false
            if (query.status != null && task.status != query.status) return@filter false
            if (fromInstant != null || toInstant != null) {
                val deadline = task.deadline ?: return@filter false
                val instant = Time.parseApiInstant(deadline)
                if (fromInstant != null && instant < fromInstant) return@filter false
                if (toInstant != null && instant > toInstant) return@filter false
            }
            true
        }
        return TaskOrdering.sorted(matched, zone)
    }
}
