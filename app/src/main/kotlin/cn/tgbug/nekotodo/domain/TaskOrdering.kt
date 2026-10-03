package cn.tgbug.nekotodo.domain

import cn.tgbug.nekotodo.data.Task
import java.time.LocalDate
import java.time.ZoneId

/**
 * 列表顺序的规则(与后端 priority 语义严格对应):
 *
 * - 排序主键是**用户时区下的截止日**,次键才是 priority;
 * - priority 只是"同一截止日组内的 1-based 位次",组内无并列无空位;
 * - 无截止时间的任务自成一组,永远排在最后。
 *
 * 因此「拖动到第几位」必须换算成组内位次才能发给 `POST /tasks/{id}/move`,
 * 且只能在同一截止日组内移动。这些换算全是纯函数,单测覆盖。
 */
object TaskOrdering {

    /** 无截止时间的任务归到同一个"最大日期"组,从而自然排在最后。 */
    private val NO_DEADLINE: LocalDate = LocalDate.MAX

    private fun sortKey(task: Task, zone: ZoneId): LocalDate =
        task.deadline?.let { Time.localDateOf(Time.parseApiInstant(it), zone) } ?: NO_DEADLINE

    fun sameDayGroup(a: Task, b: Task, zone: ZoneId): Boolean =
        sortKey(a, zone) == sortKey(b, zone)

    fun comparator(zone: ZoneId): Comparator<Task> =
        compareBy<Task> { sortKey(it, zone) }.thenBy { it.priority }

    fun sorted(tasks: List<Task>, zone: ZoneId): List<Task> =
        tasks.sortedWith(comparator(zone))

    /** 参照任务所在截止日组的排序结果,且已把被移动的任务摘出去。 */
    private fun groupWithout(
        tasks: List<Task>,
        zone: ZoneId,
        reference: Task,
        movedId: Long,
    ): List<Task> = sorted(tasks, zone).filter { sameDayGroup(it, reference, zone) && it.id != movedId }

    /**
     * 把 [draggedId] 插到 [beforeId] 之前,返回发给 move 接口的组内位次。
     * 返回 null 表示不允许:跨了截止日组,或两者不构成有效移动。
     */
    fun positionBefore(tasks: List<Task>, draggedId: Long, beforeId: Long, zone: ZoneId): Int? {
        if (draggedId == beforeId) return null
        val dragged = tasks.firstOrNull { it.id == draggedId } ?: return null
        val target = tasks.firstOrNull { it.id == beforeId } ?: return null
        if (!sameDayGroup(dragged, target, zone)) return null
        val index = groupWithout(tasks, zone, target, draggedId).indexOfFirst { it.id == beforeId }
        return if (index < 0) null else index + 1
    }

    /** 拖到本组末尾(Web 端拖到空白区域的行为)。null 表示该组内没有别的任务。 */
    fun positionAtGroupEnd(tasks: List<Task>, draggedId: Long, zone: ZoneId): Int? {
        val dragged = tasks.firstOrNull { it.id == draggedId } ?: return null
        val group = groupWithout(tasks, zone, dragged, draggedId)
        return if (group.isEmpty()) null else group.size + 1
    }

    /** 任务在其自己的截止日组内的当前 1-based 位次(各组各自从 1 开始)。 */
    fun positionOf(tasks: List<Task>, taskId: Long, zone: ZoneId): Int? {
        val task = tasks.firstOrNull { it.id == taskId } ?: return null
        val group = sorted(tasks, zone).filter { sameDayGroup(it, task, zone) }
        val index = group.indexOfFirst { it.id == taskId }
        return if (index < 0) null else index + 1
    }

    /**
     * 拖动松手后应发给 move 接口的组内位次。
     *
     * [visibleOrder] 是界面上的顺序(已包含用户拖动后的结果),[tasks] 是全部任务
     * (拖动计算始终基于全部任务,即使界面正在按分类筛选)。
     *
     * 被拖任务后面的那个可见任务决定它"插到谁之前";若后面没有任务、或后面那个属于
     * 其它截止日组,都等价于落到**本组末尾**——这一支必须显式处理,否则"拖到组末尾"
     * 会因为 positionBefore 跨组返回 null 而被误判成无效移动。
     */
    fun positionAfterDrop(
        tasks: List<Task>,
        visibleOrder: List<Task>,
        draggedId: Long,
        zone: ZoneId,
    ): Int? {
        val index = visibleOrder.indexOfFirst { it.id == draggedId }
        if (index < 0) return null
        val next = visibleOrder.getOrNull(index + 1)
        return if (next == null || !sameDayGroup(visibleOrder[index], next, zone)) {
            positionAtGroupEnd(tasks, draggedId, zone)
        } else {
            positionBefore(tasks, draggedId, next.id, zone)
        }
    }

    /**
     * 操作菜单里的「上移 / 下移」(offset 取 -1 或 +1)。
     *
     * 统一推导:设任务在组内下标为 i、目标槽位为 i + offset。
     * 目标槽位后面还有元素时就是"插到那个元素之前",它在"摘掉自己"的组里下标为 (i+offset);
     * 若目标槽位已在组末尾,则等价于插到组末尾(组内元素数 + 1)。
     */
    fun positionByOffset(tasks: List<Task>, taskId: Long, offset: Int, zone: ZoneId): Int? {
        if (offset == 0) return null
        val task = tasks.firstOrNull { it.id == taskId } ?: return null
        val group = sorted(tasks, zone).filter { sameDayGroup(it, task, zone) }
        val index = group.indexOfFirst { it.id == taskId }
        if (index < 0) return null
        if (group.size <= 1) return null
        val targetSlot = index + offset
        if (targetSlot < 0 || targetSlot > group.lastIndex) return null
        return if (targetSlot == group.lastIndex) group.size else targetSlot + 1
    }
}
