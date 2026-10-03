package cn.tgbug.nekotodo.domain

import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import java.text.Collator
import java.time.ZoneId
import java.util.Locale

data class CategoryCount(val name: String, val incomplete: Int)

object TaskGrouping {

    /** 侧栏用:只统计有分类的任务,计数是未完成数,按中文排序。 */
    fun categories(tasks: List<Task>): List<CategoryCount> {
        val incompleteCounts = HashMap<String, Int>()
        for (task in tasks) {
            val category = task.category?.takeIf { it.isNotBlank() } ?: continue
            val increment = if (task.status == TaskStatus.COMPLETED) 0 else 1
            incompleteCounts[category] = (incompleteCounts[category] ?: 0) + increment
        }
        val collator = Collator.getInstance(Locale.CHINA)
        return incompleteCounts
            .map { CategoryCount(it.key, it.value) }
            .sortedWith(compareBy(collator) { it.name })
    }

    /** 未完成在前、已完成在后,各自保持传入顺序。 */
    fun splitByCompletion(tasks: List<Task>): Pair<List<Task>, List<Task>> =
        tasks.filter { it.status != TaskStatus.COMPLETED } to tasks.filter { it.status == TaskStatus.COMPLETED }

    /**
     * 拖动被拖任务时,允许落在的下标范围。
     *
     * 列表已按(截止日, priority)排序,因此同一截止日组的任务在列表里是连续的一段;
     * 被拖任务只允许在这段之内移动,越界即"物理挡住",也就自然不会跨截止日组。
     * 返回 null 表示该任务不在列表中。
     */
    fun reorderBounds(visible: List<Task>, draggedId: Long, zone: ZoneId): IntRange? {
        val index = visible.indexOfFirst { it.id == draggedId }
        if (index < 0) return null
        val dragged = visible[index]
        var start = index
        while (start > 0 && TaskOrdering.sameDayGroup(visible[start - 1], dragged, zone)) start--
        var end = index
        while (end < visible.lastIndex && TaskOrdering.sameDayGroup(visible[end + 1], dragged, zone)) end++
        return start..end
    }
}
