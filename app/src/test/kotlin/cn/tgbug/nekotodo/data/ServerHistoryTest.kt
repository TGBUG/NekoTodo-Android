package cn.tgbug.nekotodo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerHistoryTest {

    @Test
    fun `最近使用的地址排在最前`() {
        val result = ServerHistory.remember(listOf("http://a/", "http://b/"), "http://c/")

        assertEquals(listOf("http://c/", "http://a/", "http://b/"), result)
    }

    @Test
    fun `重复的地址被提到最前而不是重复记录`() {
        val result = ServerHistory.remember(listOf("http://a/", "http://b/", "http://c/"), "http://b/")

        assertEquals(listOf("http://b/", "http://a/", "http://c/"), result)
    }

    @Test
    fun `历史长度有上限`() {
        val existing = (1..ServerHistory.MAX_ENTRIES).map { "http://h$it/" }

        val result = ServerHistory.remember(existing, "http://new/")

        assertEquals(ServerHistory.MAX_ENTRIES, result.size)
        assertEquals("http://new/", result.first())
    }

    @Test
    fun `空白输入不写入历史`() {
        val existing = listOf("http://a/")

        assertEquals(existing, ServerHistory.remember(existing, "   "))
    }

    @Test
    fun `编解码往返保持顺序`() {
        val entries = listOf("http://a/", "https://b/", "192.168.1.10:8000")

        assertEquals(entries, ServerHistory.decode(ServerHistory.encode(entries)))
    }

    @Test
    fun `解码空值与空串都得到空列表`() {
        assertEquals(emptyList<String>(), ServerHistory.decode(null))
        assertEquals(emptyList<String>(), ServerHistory.decode(""))
        assertEquals(emptyList<String>(), ServerHistory.decode("\n\n"))
    }
}
