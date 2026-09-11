package win.catgo.gpt.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import win.catgo.gpt.model.ServerConfig

class HttpWebSocketTest {
    @Test fun explicitHttpCanUpgradeToWsWithOriginRestriction() {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getLoopbackAddress(), 0)
            val received = AtomicReference<String>()
            val failure = AtomicReference<Throwable>()
            val done = CountDownLatch(1)
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    webSocket.send("echo:$text")
                }
            }))
            val url = server.url("/")
            val config = ServerConfig(url.host, url.port, "local-demo", "http")
            val client = ServerHttpPolicy.restrictToServer(ServerHttpPolicy.apply(OkHttpClient.Builder()), config).build()
            val socket = client.newWebSocket(Request.Builder().url(config.webSocketBaseUrl + "/api/pty").build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("local-test") }
                    override fun onMessage(webSocket: WebSocket, text: String) { received.set(text); done.countDown() }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { failure.set(t); done.countDown() }
                })
            try {
                assertTrue("WebSocket response timed out", done.await(5, TimeUnit.SECONDS))
                assertNull(failure.get())
                assertEquals("echo:local-test", received.get())
                assertEquals("/api/pty", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
            } finally {
                socket.cancel()
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }
}
