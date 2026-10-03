package cn.tgbug.nekotodo.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NekoTodoRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var session: SessionState
    private lateinit var repository: NekoTodoRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session = SessionState()
        repository = NekoTodoRepository(session, ApiProvider(session))
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
    fun `登录成功后会话里记下服务器地址与 token`() = runTest {
        server.enqueue(json("""{"token":"jwt-abc"}"""))

        repository.login(server.url("/").toString(), "alice", "password123")

        assertEquals("jwt-abc", session.token)
        assertTrue(repository.isLoggedIn)
    }

    @Test
    fun `服务器返回的 detail 成为异常消息`() = runTest {
        session.serverUrl = server.url("/").toString()
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail":"描述为空"}"""),
        )

        val error = runCatching { repository.createTask("", "", null, null) }.exceptionOrNull()

        assertTrue("实际异常:$error", error is ApiException)
        assertEquals(400, (error as ApiException).status)
        assertEquals("描述为空", error.message)
    }

    @Test
    fun `无 detail 字段时回落到 HTTP 状态描述`() = runTest {
        session.serverUrl = server.url("/").toString()
        server.enqueue(MockResponse().setResponseCode(404).setBody("not json at all"))

        val error = runCatching { repository.tasks() }.exceptionOrNull()

        assertTrue("实际异常:$error", error is ApiException)
        assertEquals(404, (error as ApiException).status)
    }

    @Test
    fun `连不上服务器时状态码为 0`() = runTest {
        session.serverUrl = server.url("/").toString()
        server.shutdown()

        val error = runCatching { repository.tasks() }.exceptionOrNull()

        assertTrue("实际异常:$error", error is ApiException)
        assertEquals(0, (error as ApiException).status)
    }

    @Test
    fun `提交源信息以 multipart 发出文本与文件`() = runTest {
        session.serverUrl = server.url("/").toString()
        server.enqueue(json("""{"source_info_id":1,"run_id":"run-1"}"""))

        val accepted = repository.submitSourceInfo("数学:练习册 p50", emptyList())

        assertEquals(1L, accepted.sourceInfoId)
        assertEquals("run-1", accepted.runId)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/source-infos", recorded.path)
        assertTrue(
            "Content-Type 应为 multipart,实际:${recorded.getHeader("Content-Type")}",
            recorded.getHeader("Content-Type")?.startsWith("multipart/form-data") == true,
        )
        assertTrue("请求体应包含文本内容", recorded.body.readUtf8().contains("数学:练习册 p50"))
    }

    @Test
    fun `登出只清掉 token 保留服务器地址`() {
        session.serverUrl = "https://todo.example.com/"
        session.token = "jwt-abc"

        repository.logout()

        assertEquals("https://todo.example.com/", session.serverUrl)
        assertEquals(null, session.token)
    }
}
