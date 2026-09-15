package win.catgo.gpt.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockWebServer
import win.catgo.gpt.data.EncryptedCookieJar
import win.catgo.gpt.data.SessionStorage
import win.catgo.gpt.data.SettingsStore
import win.catgo.gpt.model.ServerConfig

class MemorySessionStorage : SessionStorage {
    @Volatile private var value: String? = null
    override fun read() = value
    override fun write(value: String) { this.value = value }
    override fun clear() { value = null }
}

class LocalHermesFixture : AutoCloseable {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val server = MockWebServer().apply { start(java.net.InetAddress.getLoopbackAddress(), 0) }
    val config = ServerConfig(server.url("/").host, server.port, "local-demo", "http")
    val settings = SettingsStore(context).apply { clear(); save(config) }
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val cookies = MemorySessionStorage()
    val credentials = MemorySessionStorage()
    fun client() = HermesClient(context, settings, EncryptedCookieJar(cookies, json), json, credentials)
    override fun close() { server.close(); settings.clear() }
}
