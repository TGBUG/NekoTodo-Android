package cn.tgbug.nekotodo.data

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var session: SessionState
    private lateinit var provider: ApiProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session = SessionState()
        provider = ApiProvider(session)
        session.serverUrl = server.url("/").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `登录请求的路径 方法与响应解析都正确`() = runTest {
        server.enqueue(json("""{"token":"jwt-abc"}"""))

        val token = provider.api().login(LoginBody("alice", "password123"))

        assertEquals("jwt-abc", token.token)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/auth/login", recorded.path)
    }

    @Test
    fun `任务列表能反序列化含 source_item_id 与 null 截止时间`() = runTest {
        server.enqueue(
            json(
                """[{"id":1,"description":"买牛奶","status":"incomplete","details":"",
                   "deadline":null,"priority":1,"category":"生活","source_item_id":7}]""",
            ),
        )

        val tasks = provider.api().listTasks()

        assertEquals(1, tasks.size)
        assertEquals(TaskStatus.INCOMPLETE, tasks[0].status)
        assertEquals(7L, tasks[0].sourceItemId)
        assertNull(tasks[0].deadline)
    }

    @Test
    fun `未知字段不会导致反序列化失败`() = runTest {
        server.enqueue(
            json("""[{"id":1,"description":"买牛奶","status":"completed","details":"",
                   "deadline":null,"priority":1,"category":null,"source_item_id":null,
                   "something_new":"来自未来版本的字段"}]"""),
        )

        assertEquals(TaskStatus.COMPLETED, provider.api().listTasks()[0].status)
    }

    @Test
    fun `编辑任务时 deadline 与 category 以显式 null 发出表示清空`() = runTest {
        server.enqueue(json("""{"id":1,"description":"买牛奶","status":"incomplete"}"""))

        provider.api().patchTask(1, taskFormBody(description = "买牛奶", details = "", deadline = null, category = null))

        val body = server.takeRequest().body.readUtf8()
        assertTrue("实际请求体:$body", body.contains("\"deadline\":null"))
        assertTrue("实际请求体:$body", body.contains("\"category\":null"))
    }

    @Test
    fun `状态切换只发送 status 字段`() = runTest {
        server.enqueue(json("""{"id":1,"description":"买牛奶","status":"completed"}"""))

        provider.api().patchTask(1, taskStatusBody(TaskStatus.COMPLETED))

        val body = server.takeRequest().body.readUtf8()
        assertEquals("""{"status":"completed"}""", body)
    }

    @Test
    fun `同主机的请求会带上 Bearer token`() = runTest {
        session.token = "tok-123"
        server.enqueue(json("""{"id":1,"username":"alice","timezone":"UTC"}"""))

        provider.api().me()

        assertEquals("Bearer tok-123", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `不带协议的地址也能解析出主机`() {
        // 鉴权拦截器靠解析 host 来判断"是不是当前这台服务器"。
        // 用户很可能只写 "192.168.1.10:8000",若这串解析不出 host,token 就会被静默丢掉。
        val parsed = ApiProvider.normalizeBaseUrl("192.168.1.10:8000").toHttpUrlOrNull()

        assertNotNull(parsed)
        assertEquals("192.168.1.10", parsed?.host)
        assertEquals(8000, parsed?.port)
    }

    @Test
    fun `token 不会发给非当前配置的主机`() {
        session.token = "tok-123"
        session.serverUrl = "https://todo.example.com/"
        server.enqueue(json("""{"ok":true}"""))

        provider.okHttp.newCall(Request.Builder().url(server.url("/files/abc")).build()).execute().close()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `不带协议时补 https 而不是明文 http`() {
        assertEquals("https://nekotodo.example.com/", ApiProvider.normalizeBaseUrl("nekotodo.example.com"))
        assertEquals("https://192.168.1.10:8000/", ApiProvider.normalizeBaseUrl("192.168.1.10:8000"))
        // 显式写明才走明文
        assertEquals("http://192.168.1.10:8000/", ApiProvider.normalizeBaseUrl("http://192.168.1.10:8000"))
        assertEquals("http://127.0.0.1:8000/", ApiProvider.normalizeBaseUrl(" http://127.0.0.1:8000/ "))
        assertEquals("https://todo.example.com/", ApiProvider.normalizeBaseUrl("https://todo.example.com"))
    }

    @Test
    fun `只有显式 http 才判定为明文`() {
        assertTrue(ApiProvider.usesCleartext("http://192.168.1.10:8000"))
        assertFalse(ApiProvider.usesCleartext("192.168.1.10:8000")) // 缺省补 https
        assertFalse(ApiProvider.usesCleartext("nekotodo.example.com"))
        assertFalse(ApiProvider.usesCleartext("https://nekotodo.example.com"))
        assertFalse(ApiProvider.usesCleartext("  "))
    }
}
