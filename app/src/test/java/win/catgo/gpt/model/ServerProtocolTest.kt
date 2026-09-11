package win.catgo.gpt.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ServerProtocolTest {
    @Test fun explicitHttpSupportsIpv6AndWs() {
        val config = ServerConfig("http://[::1]:8080/chat", 8080, "demo", "http")
        assertNull(config.validate())
        assertEquals("http://[::1]:8080", config.baseUrl)
        assertEquals("ws://[::1]:8080", config.webSocketBaseUrl)
        assertEquals("wss://example.com", ServerConfig("example.com", 443, "demo").webSocketBaseUrl)
    }
    @Test fun mismatchedOrUnsupportedProtocolsAreRejected() {
        assertNotNull(ServerConfig("http://example.com", 80, "demo").validate())
        assertNotNull(ServerConfig("https://example.com", 443, "demo", "http").validate())
        assertNotNull(ServerConfig("example.com", 80, "demo", "ftp").validate())
        assertNotNull(ServerConfig("http://u:p@example.com", 80, "demo", "http").validate())
    }
    @Test fun oldSerializedConfigurationDefaultsToHttps() {
        val config = Json.decodeFromString<ServerConfig>("""{"host":"example.com","port":443,"username":"demo"}""")
        assertEquals("https", config.scheme)
    }
}
