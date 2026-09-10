package win.catgo.gpt

import android.content.Context
import java.util.UUID
import kotlinx.serialization.json.Json
import win.catgo.gpt.data.EncryptedCookieJar
import win.catgo.gpt.data.SecureSessionStore
import win.catgo.gpt.data.SettingsStore
import win.catgo.gpt.network.HermesClient

class AppContainer(context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    val settingsStore = SettingsStore(context)
    val cookieJar = EncryptedCookieJar(SecureSessionStore(context), json)
    val client = HermesClient(context.applicationContext, settingsStore, cookieJar, json)

    private val attachPreferences =
        context.getSharedPreferences("catgo_install", Context.MODE_PRIVATE)
    @Volatile
    private var currentAttachId: String =
        attachPreferences.getString("attach_id", null) ?: createAttachId().also(::persistAttachId)

    @Synchronized
    fun attachId(fresh: Boolean): String {
        val selected = AttachIdPolicy.select(currentAttachId, fresh, ::createAttachId)
        if (selected != currentAttachId) {
            currentAttachId = selected
            persistAttachId(selected)
        }
        return selected
    }

    private fun persistAttachId(value: String) {
        attachPreferences.edit().putString("attach_id", value).apply()
    }

    private fun createAttachId(): String = UUID.randomUUID().toString().replace("-", "")
}

internal object AttachIdPolicy {
    fun select(current: String, fresh: Boolean, generate: () -> String): String =
        if (fresh) generate() else current
}
