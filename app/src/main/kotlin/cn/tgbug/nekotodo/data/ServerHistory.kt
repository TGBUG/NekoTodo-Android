package cn.tgbug.nekotodo.data

/**
 * 服务器地址的历史记录:最近使用在前、去重、限长。
 * 抽成纯函数是为了能直接单测——DataStore 本身不方便在单测里跑。
 */
object ServerHistory {

    const val MAX_ENTRIES = 5

    private const val SEPARATOR = "\n"

    fun decode(stored: String?): List<String> =
        stored?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

    fun encode(entries: List<String>): String = entries.joinToString(SEPARATOR)

    fun remember(existing: List<String>, entry: String): List<String> {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return existing
        return (listOf(trimmed) + existing.filterNot { it == trimmed }).take(MAX_ENTRIES)
    }
}
