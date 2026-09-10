package win.catgo.gpt.network

import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in integration test; creates a labeled conversation, never changes server configuration. */
class HermesProtocolLiveTest {
    @Test fun newSessionExactInputReplyAndResume() {
        val base = System.getenv("CATGO_LIVE_BASE")
        assumeTrue("Live testing is opt-in", !base.isNullOrBlank())
        val username = requireNotNull(System.getenv("CATGO_LIVE_USER"))
        val password = requireNotNull(System.getenv("CATGO_LIVE_PASSWORD"))
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        val platform = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        val issuers = File("src/main/res/raw/hermes_ca_intermediates.pem").inputStream().use {
            CertificateFactory.getInstance("X.509").generateCertificates(it).map { cert -> cert as X509Certificate }
        }
        val trust = ChainCompletingTrustManager(platform, issuers)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        val cookies = mutableListOf<Cookie>()
        val client = OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust)
            .apply {
                System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let {
                    val proxyUri = java.net.URI(it)
                    proxy(java.net.Proxy(java.net.Proxy.Type.HTTP,
                        java.net.InetSocketAddress(proxyUri.host, proxyUri.port)))
                }
            }
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, values: List<Cookie>) {
                    synchronized(cookies) {
                        values.forEach { new -> cookies.removeAll { it.name == new.name }; cookies.add(new) }
                    }
                }
                override fun loadForRequest(url: HttpUrl) = synchronized(cookies) { cookies.filter { it.matches(url) } }
            }).callTimeout(25, TimeUnit.SECONDS).build()
        val json = Json { ignoreUnknownKeys = true }
        val media = "application/json".toMediaType()
        fun request(path: String, body: String? = null): JsonObject {
            val builder = Request.Builder().url(base + path)
            if (body != null) builder.post(body.toRequestBody(media))
            return client.newCall(builder.build()).execute().use {
                check(it.isSuccessful) { "HTTP " + it.code + " on " + path }
                json.parseToJsonElement(it.body!!.string()).jsonObject
            }
        }
        request("/auth/password-login", buildJsonObject {
            put("provider", "basic"); put("username", username); put("password", password); put("next", "/chat")
        }.toString())
        assertTrue(request("/api/model/options?profile=default")["providers"]!!.jsonArray.isNotEmpty())
        val attach = UUID.randomUUID().toString()
        val outputs = LinkedBlockingQueue<String>()
        val ids = LinkedBlockingQueue<String>()
        fun connect(resume: String?): WebSocket {
            val decoder = TerminalOutputDecoder()
            val ticket = request("/api/auth/ws-ticket", "")["ticket"]!!.jsonPrimitive.content
            val url = base!!.replaceFirst("https://", "wss://") +
                "/api/pty?profile=default&channel=chat-" + UUID.randomUUID() + "&attach=" + attach + "&ticket=" + ticket +
                if (resume == null) "&fresh=1" else "&resume=" + resume
            return client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) { ws.send("\u001b[RESIZE:90;32]") }
                override fun onMessage(ws: WebSocket, bytes: ByteString) = onMessage(ws, bytes.utf8())
                override fun onMessage(ws: WebSocket, text: String) {
                    val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    if (obj?.get("type")?.jsonPrimitive?.content == "resume") {
                        ids.offer(obj["id"]!!.jsonPrimitive.content)
                    } else outputs.offer(decoder.decode(text))
                }
                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    outputs.offer("CONNECTION_FAILURE")
                }
            })
        }
        var socket: WebSocket? = null
        try {
            socket = connect(null)
            var startup = ""
            val readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(50)
            while (System.nanoTime() < readyDeadline && !startup.contains("❯") &&
                !(startup.contains("/help") && startup.contains("commands"))) {
                startup += outputs.poll(1, TimeUnit.SECONDS).orEmpty()
            }
            assertTrue("Hermes must expose an input prompt before sending", startup.contains("❯") ||
                startup.contains("/help") && startup.contains("commands"))
            val marker = "CATGO_V110_" + UUID.randomUUID().toString().take(8)
            val prompt = marker + " 协议回归测试。不要调用工具，只回复：中文连接正常。"
            // Reproduce leftover CLI input, then use exactly the app's framing.
            socket.send("lll")
            Thread.sleep(100)
            val frames = TerminalInput.frames(prompt)
            assertTrue(socket.send(frames[0]))
            Thread.sleep(100)
            assertTrue(socket.send(frames[1]))
            var id: String? = ids.poll()
            var messages = emptyList<JsonObject>()
            var finalReply = ""
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(100)
            while (System.nanoTime() < deadline && finalReply.isBlank()) {
                Thread.sleep(1000)
                id = ids.poll() ?: id
                if (id == null) {
                    id = request("/api/sessions?limit=20")["sessions"]!!.jsonArray
                        .map { it.jsonObject }.firstOrNull {
                            it["preview"]?.jsonPrimitive?.content.orEmpty().contains(marker)
                        }?.get("id")?.jsonPrimitive?.content
                }
                if (id != null) {
                    messages = request("/api/sessions/" + id + "/messages?limit=500&order=latest")["messages"]!!
                        .jsonArray.map { it.jsonObject }
                    finalReply = messages.lastOrNull {
                        it["role"]?.jsonPrimitive?.content == "assistant" &&
                            it["content"]?.jsonPrimitive?.content.orEmpty().isNotBlank() &&
                            (it["tool_calls"] == null || it["tool_calls"] == JsonNull ||
                                it["tool_calls"]?.jsonArray?.isEmpty() == true)
                    }?.get("content")?.jsonPrimitive?.content.orEmpty()
                }
            }
            val users = messages.filter { it["role"]?.jsonPrimitive?.content == "user" }
            assertEquals("No injected lll, duplicates or lost Chinese text", listOf(prompt),
                users.map { it["content"]!!.jsonPrimitive.content })
            assertTrue("Server must return requested Chinese answer", finalReply.contains("中文连接正常"))
            assertNotNull(id)
            println("LIVE PASS: exact user text, Chinese reply, no duplicate; session=" + id)
            socket.close(1000, "test reconnect")
            ids.clear()
            outputs.clear()
            socket = connect(id)
            var replay = ""
            val resumeDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40)
            while (System.nanoTime() < resumeDeadline && !replay.contains("❯") &&
                !(replay.contains("/help") && replay.contains("commands"))) {
                replay += outputs.poll(1, TimeUnit.SECONDS).orEmpty()
            }
            assertTrue("Resumed terminal must expose prompt; tail=" + replay.takeLast(300),
                replay.contains("❯") || replay.contains("/help") && replay.contains("commands"))
            val announcedId = ids.poll()
            if (announcedId != null) assertEquals(id, announcedId)
            val followup = marker + " 恢复后测试，请只回复：恢复正常。"
            val nextFrames = TerminalInput.frames(followup)
            socket.send(nextFrames[0])
            Thread.sleep(100)
            socket.send(nextFrames[1])
            var resumed = emptyList<JsonObject>()
            val followupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(80)
            while (System.nanoTime() < followupDeadline) {
                Thread.sleep(1000)
                resumed = request("/api/sessions/" + id + "/messages?limit=500&order=latest")["messages"]!!
                    .jsonArray.map { it.jsonObject }
                if (resumed.any { it["role"]?.jsonPrimitive?.content == "assistant" &&
                    it["content"]?.jsonPrimitive?.content.orEmpty().contains("恢复正常") }) break
            }
            assertEquals(listOf(prompt, followup), resumed.filter { it["role"]?.jsonPrimitive?.content == "user" }
                .map { it["content"]!!.jsonPrimitive.content })
            assertTrue(resumed.any { it["role"]?.jsonPrimitive?.content == "assistant" &&
                it["content"]?.jsonPrimitive?.content.orEmpty().contains("恢复正常") })
            val persisted = request("/api/sessions/" + id + "/messages?limit=500&order=latest")["messages"]!!.jsonArray
            assertEquals(resumed.size, persisted.size)
            println("LIVE PASS: resume preserved session/history; model options readable")
        } finally {
            socket?.close(1000, "test complete")
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
