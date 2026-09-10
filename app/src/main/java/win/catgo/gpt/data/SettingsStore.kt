package win.catgo.gpt.data

import android.content.Context
import win.catgo.gpt.model.ServerConfig

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("catgo_settings", Context.MODE_PRIVATE)

    fun load(): ServerConfig? {
        val host = preferences.getString(KEY_HOST, null)?.takeIf { it.isNotBlank() } ?: return null
        return ServerConfig(
            host = host,
            port = preferences.getInt(KEY_PORT, 443),
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
        )
    }

    fun save(config: ServerConfig) {
        preferences.edit()
            .putString(KEY_HOST, config.normalizedHost)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_USERNAME, config.username.trim())
            .apply()
    }

    fun voiceRepliesEnabled(): Boolean = preferences.getBoolean(KEY_VOICE_REPLIES, false)

    fun setVoiceRepliesEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_VOICE_REPLIES, enabled).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USERNAME = "username"
        const val KEY_VOICE_REPLIES = "voice_replies"
    }
}
