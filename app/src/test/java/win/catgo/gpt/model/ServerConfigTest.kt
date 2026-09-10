package win.catgo.gpt.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerConfigTest {
    @Test
    fun stripsSchemePortAndPathFromHost() {
        val config = ServerConfig("https://hermes.example.com:8443/chat", 8443, "test-user")

        assertEquals("hermes.example.com", config.normalizedHost)
        assertEquals("https://hermes.example.com:8443", config.baseUrl)
    }

    @Test fun supportsIpv6AndUsesTheExplicitPort() {
        assertEquals("https://[::1]:8443", ServerConfig("[::1]", 8443, "u").baseUrl)
        assertEquals("https://[::1]:8443", ServerConfig("::1", 8443, "u").baseUrl)
    }
    @Test fun rejectsHttpCredentialsQueriesAndMalformedHosts() {
        listOf("http://example.com", "https://u:p@example.com", "https://example.com?q=x",
            "https://example.com#x", "https://", "example.com @evil.example", "ftp://example.com").forEach {
            org.junit.Assert.assertNotNull(it, ServerConfig(it, 443, "u").validate())
        }
    }
    @Test
    fun acceptsValidConfiguration() {
        assertNull(ServerConfig("agent.example.com", 443, "user").validate())
    }

    @Test
    fun rejectsInvalidPort() {
        assertEquals("端口必须在 1 到 65535 之间", ServerConfig("agent.example.com", 0, "user").validate())
    }
}
