package cn.tgbug.nekotodo.data

import cn.tgbug.nekotodo.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 非 2xx 一律抛这个;status == 0 表示根本没连上服务器。 */
class ApiException(val status: Int, override val message: String) : Exception(message)

/** 当前会话。两个字段都会在运行期变化(切换服务器、登录、登出),因此用可变持有者。 */
class SessionState {
    @Volatile
    var serverUrl: String = ""

    @Volatile
    var token: String? = null

    val hasSession: Boolean get() = serverUrl.isNotBlank() && !token.isNullOrEmpty()
}

object ApiJson {
    val instance = Json {
        ignoreUnknownKeys = true
    }
}

/** 把 Retrofit 的调用结果收敛成 ApiException,让上层只需处理一种异常。 */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw ApiException(e.code(), e.detailMessage())
} catch (e: IOException) {
    throw ApiException(0, "无法连接服务器:${e.message ?: "网络错误"}")
}

private fun HttpException.detailMessage(): String {
    val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val detail = raw?.let { body ->
        runCatching { ApiJson.instance.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content }
            .getOrNull()
    }
    return detail ?: message()
}

class ApiProvider(private val session: SessionState) {

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = session.token
        // 必须先补全协议再解析:用户很可能只填 "192.168.1.10:8000",
        // 那样的字符串 toHttpUrlOrNull() 会直接返回 null,导致所有请求都丢掉凭据。
        val sessionHost = normalizeBaseUrl(session.serverUrl).toHttpUrlOrNull()?.host
        // 只给当前配置的那台服务器带凭据,避免 token 被发到别的主机。
        val authenticated =
            if (token.isNullOrEmpty() || sessionHost == null || request.url.host != sessionHost) {
                request
            } else {
                request.newBuilder().header("Authorization", "Bearer $token").build()
            }
        chain.proceed(authenticated)
    }

    /**
     * Retrofit 与 Coil 共用:Coil 请求的是绝对 URL(`/files/{uuid}`),
     * 所以同一个鉴权拦截器就能让图片也带上凭据。
     */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    private var cachedUrl: String? = null
    private var cachedApi: Api? = null

    /** 服务器地址变了才重建 Retrofit;token 由拦截器实时读取,不必重建。 */
    @Synchronized
    fun api(): Api {
        val normalized = normalizeBaseUrl(session.serverUrl)
        cachedApi?.let { if (cachedUrl == normalized) return it }
        val created = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttp)
            .addConverterFactory(ApiJson.instance.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Api::class.java)
        cachedUrl = normalized
        cachedApi = created
        return created
    }

    /** 图片的绝对地址(Coil 用)。 */
    fun fileUrl(fileUuid: String): String =
        normalizeBaseUrl(session.serverUrl) + "files/" + fileUuid

    companion object {
        /**
         * 允许用户只填 `nekotodo.example.com`;补全协议与结尾斜杠。
         *
         * 缺省协议取 **https** 而不是 http:公网部署的形态就是 https(后端把 TLS 交给反代),
         * 若默认补 http,用户只填域名时会静默走明文——既连不上(或遭重定向),
         * 也不会触发未加密警告。要明文就必须显式写 `http://`,那时才会给出警告。
         */
        fun normalizeBaseUrl(raw: String): String {
            val trimmed = raw.trim()
            val withScheme =
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
                else "https://$trimmed"
            return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
        }

        /**
         * 后端本身不做 TLS(设计上交给 nginx 之类反代),所以明文是合法配置;
         * 但局域网里 token 会被同网段嗅探到,因此登录页要给出提示。
         */
        fun usesCleartext(raw: String): Boolean =
            raw.isNotBlank() && normalizeBaseUrl(raw).startsWith("http://")
    }
}
