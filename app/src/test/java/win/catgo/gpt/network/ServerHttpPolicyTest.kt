package win.catgo.gpt.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ServerHttpPolicyTest {
    @Test fun redirectsNeverForwardAnAuthenticationBodyOrDowngradeTls() {
        val client = ServerHttpPolicy.apply(OkHttpClient.Builder()).build()
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.start(java.net.InetAddress.getLoopbackAddress(), 0)
            second.start(java.net.InetAddress.getLoopbackAddress(), 0)
            for (code in listOf(301, 302, 303, 307, 308)) {
                first.enqueue(MockResponse().setResponseCode(code).addHeader("Location", second.url("/capture")))
                client.newCall(Request.Builder().url(first.url("/auth/password-login"))
                    .post("local-fake-credential".toRequestBody()).build()).execute().use {
                    assertEquals(code, it.code)
                }
            }
            assertEquals(0, second.requestCount)
        } }
    }
}
