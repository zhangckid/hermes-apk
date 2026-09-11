package win.catgo.gpt.network

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import win.catgo.gpt.model.ServerConfig

class ServerOriginTest {
    @Test fun permitsOnlyExplicitOriginAndNeverDowngrades() {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getLoopbackAddress(), 0)
            val url = server.url("/api/auth/me")
            val config = ServerConfig(url.host, url.port, "demo", "http")
            fun client(c: ServerConfig) = ServerHttpPolicy.restrictToServer(
                ServerHttpPolicy.apply(OkHttpClient.Builder()), c).build()
            server.enqueue(MockResponse().setBody("{}"))
            client(config).newCall(Request.Builder().url(url).build()).execute().use {
                assertEquals(200, it.code)
            }
            for (blocked in listOf(config.copy(scheme = "https"), config.copy(port = 1),
                config.copy(host = "different.example.com"))) {
                try {
                    client(blocked).newCall(Request.Builder().url(url).build()).execute().close()
                    fail("Mismatched origin must be blocked before network access")
                } catch (expected: IOException) {
                    assertEquals("Request does not match the configured server origin", expected.message)
                }
            }
            assertEquals(1, server.requestCount)
        }
    }
}
