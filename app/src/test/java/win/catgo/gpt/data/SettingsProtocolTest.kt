package win.catgo.gpt.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.catgo.gpt.model.ServerConfig

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class SettingsProtocolTest {
    @Test fun savesExplicitHttpAndMigratesLegacyHttps() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("catgo_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("host", "example.com").putInt("port", 8443)
            .putString("username", "demo").commit()
        val store = SettingsStore(context)
        assertEquals("https", store.load()!!.scheme)
        store.save(ServerConfig("http://example.com/chat", 8080, "demo", "http"))
        val restored = SettingsStore(context).load()!!
        assertEquals("http://example.com:8080", restored.baseUrl)
        assertEquals("ws://example.com:8080", restored.webSocketBaseUrl)
    }
}
