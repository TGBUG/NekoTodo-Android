package cn.tgbug.nekotodo

import android.content.Context
import cn.tgbug.nekotodo.data.ApiProvider
import cn.tgbug.nekotodo.data.NekoTodoRepository
import cn.tgbug.nekotodo.data.SessionState
import cn.tgbug.nekotodo.data.SettingsStore
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

/**
 * 手写依赖注入。依赖图只有一层深,用不上 Hilt;这也让整个工程不需要任何注解处理器。
 *
 * 会话状态的读写收在这里:服务器地址与 token 必须同时更新内存与实际存储,
 * 分散到各个 ViewModel 里迟早会漏掉一边。
 */
class AppContainer(context: Context) {

    /** 需要读 content:// (上传附件) 的地方共用一个 Application context。 */
    val appContext: Context = context.applicationContext

    val session = SessionState()

    val settings = SettingsStore(appContext)

    private val apiProvider = ApiProvider(session)

    val repository = NekoTodoRepository(session, apiProvider)

    /** 图片接口需要鉴权,所以必须复用同一个带 Authorization 头的客户端。 */
    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { repository.okHttp }))
        }
        .crossfade(true)
        .build()

    /** 启动时把本地会话灌进内存。返回是否已有可用会话。 */
    suspend fun restoreSession(): Boolean {
        session.serverUrl = settings.currentServerUrl()
        session.token = settings.currentToken()
        return session.hasSession
    }

    /** 登录成功后把服务器地址与 token 一起落盘;地址历史同时更新。 */
    suspend fun login(serverUrl: String, username: String, password: String) {
        repository.login(serverUrl, username, password)
        settings.saveSession(serverUrl, session.token.orEmpty())
    }

    suspend fun logout() {
        repository.logout()
        settings.clearToken()
    }
}
