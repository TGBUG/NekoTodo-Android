package cn.tgbug.nekotodo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.tgbug.nekotodo.domain.Appearance
import cn.tgbug.nekotodo.domain.BackgroundOption
import cn.tgbug.nekotodo.domain.Palette
import cn.tgbug.nekotodo.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "nekotodo_settings")

/**
 * 本地持久化。后端没有存放这些的地方,所以它们只活在设备上:
 * 换手机或重装都要重新设置("后端冻结"的固有代价之一)。
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val token = stringPreferencesKey("token")
        val serverHistory = stringPreferencesKey("server_history")
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = intPreferencesKey("accent")
        val background = stringPreferencesKey("background")
        val customBackgroundPath = stringPreferencesKey("custom_background_path")
    }

    val serverUrl: Flow<String> = dataStore.data.map { it[Keys.serverUrl].orEmpty() }

    val token: Flow<String?> = dataStore.data.map { it[Keys.token]?.takeIf(String::isNotEmpty) }

    val serverHistory: Flow<List<String>> =
        dataStore.data.map { ServerHistory.decode(it[Keys.serverHistory]) }

    val appearance: Flow<Appearance> = dataStore.data.map { prefs ->
        Appearance(
            themeMode = ThemeMode.fromName(prefs[Keys.themeMode]),
            accent = prefs[Keys.accent] ?: Palette.DEFAULT_ACCENT,
            background = BackgroundOption.fromId(prefs[Keys.background]),
            customBackgroundPath = prefs[Keys.customBackgroundPath]?.takeIf(String::isNotBlank),
        )
    }

    suspend fun currentServerUrl(): String = dataStore.data.first()[Keys.serverUrl].orEmpty()

    suspend fun currentToken(): String? = dataStore.data.first()[Keys.token]?.takeIf(String::isNotEmpty)

    suspend fun saveSession(serverUrl: String, token: String) {
        dataStore.edit { prefs ->
            prefs[Keys.serverUrl] = serverUrl
            prefs[Keys.token] = token
            prefs[Keys.serverHistory] = ServerHistory.encode(
                ServerHistory.remember(ServerHistory.decode(prefs[Keys.serverHistory]), serverUrl),
            )
        }
    }

    /** 只换服务器地址(用于登录页预填),不动 token。 */
    suspend fun rememberServerUrl(serverUrl: String) {
        dataStore.edit { prefs ->
            prefs[Keys.serverUrl] = serverUrl
            prefs[Keys.serverHistory] = ServerHistory.encode(
                ServerHistory.remember(ServerHistory.decode(prefs[Keys.serverHistory]), serverUrl),
            )
        }
    }

    suspend fun clearToken() {
        dataStore.edit { it.remove(Keys.token) }
    }

    /** 外观改动立即落盘:界面上是即时预览,没有"保存"按钮。 */
    suspend fun saveAppearance(appearance: Appearance) {
        dataStore.edit { prefs ->
            prefs[Keys.themeMode] = appearance.themeMode.name
            prefs[Keys.accent] = appearance.accent
            prefs[Keys.background] = appearance.background.id
            appearance.customBackgroundPath
                ?.takeIf(String::isNotBlank)
                ?.let { prefs[Keys.customBackgroundPath] = it }
                ?: prefs.remove(Keys.customBackgroundPath)
        }
    }
}
