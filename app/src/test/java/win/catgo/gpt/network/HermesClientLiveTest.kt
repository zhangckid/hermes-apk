package win.catgo.gpt.network

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.catgo.gpt.data.EncryptedCookieJar
import win.catgo.gpt.data.SettingsStore
import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.PickedImage
import win.catgo.gpt.model.ServerConfig

/** Explicit opt-in only. Creates marked conversations and sends two short model requests per origin. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HermesClientLiveTest {
    @Test fun httpsAppClientEndToEnd() = exercise("CATGO_LIVE_HTTPS")
    @Test fun httpAppClientEndToEnd() = exercise("CATGO_LIVE_HTTP")

    private fun exercise(variable: String) = runBlocking {
        val base = System.getenv(variable)
        assumeTrue("Live testing requires explicit opt-in", !base.isNullOrBlank())
        val uri = URI(base!!)
        val username = requireNotNull(System.getenv("CATGO_LIVE_USER"))
        val password = requireNotNull(System.getenv("CATGO_LIVE_PASSWORD"))
        val context = ApplicationProvider.getApplicationContext<Application>()
        val config = ServerConfig(uri.host, if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80,
            username, uri.scheme)
        val settings = SettingsStore(context).apply { clear() }
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val cookieStorage = MemorySessionStorage()
        val credentialStorage = MemorySessionStorage()
        fun client() = HermesClient(context, settings, EncryptedCookieJar(cookieStorage, json), json, credentialStorage)
        val previousProxy = ProxySelector.getDefault()
        val proxyEnv = System.getenv(if (uri.scheme == "https") "HTTPS_PROXY" else "HTTP_PROXY")
            ?: System.getenv("HTTPS_PROXY")
        if (!proxyEnv.isNullOrBlank()) {
            val proxyUri = URI(proxyEnv)
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(target: URI): List<Proxy> = if (target.host == uri.host && target.port == uri.port)
                    listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyUri.host, proxyUri.port)))
                    else previousProxy?.select(target) ?: listOf(Proxy.NO_PROXY)
                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) = Unit
            })
        }
        var socket: HermesPtySocket? = null
        var phase = "login"
        var sessionId: String? = null
        val output = LinkedBlockingQueue<String>()
        val failures = LinkedBlockingQueue<String>()
        try {
            client().login(config, password)
            println("LIVE ${uri.scheme}: login and authenticated-session check PASS")
            phase = "restore and read APIs"
            val app = client()
            assertTrue(app.checkAuthenticated())
            app.getSessions()
            app.getModelOptions()
            app.getReasoning()
            val model = app.getModelInfo()
            println("LIVE ${uri.scheme}: persisted login, sessions, model options and reasoning read PASS")

            phase = "image upload"
            val bytes = ByteArrayOutputStream().use { buffer ->
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
                    .compress(Bitmap.CompressFormat.PNG, 100, buffer)
                buffer.toByteArray()
            }
            val imagePath = app.uploadImage(PickedImage("local-test", "catgo-live-test.png", "image/png", bytes))
            assertTrue(imagePath.isNotBlank())
            println("LIVE ${uri.scheme}: image upload PASS")

            val attach = UUID.randomUUID().toString()
            suspend fun connect(resume: String?) {
                output.clear(); failures.clear()
                val terminalScreen = TerminalTextScreen()
                socket = HermesPtySocket(app, object : HermesPtySocket.Listener {
                    override fun onRawOutput(bytes: ByteArray) {
                        terminalScreen.append(bytes)
                        val replies = terminalScreen.takeReplies()
                        runBlocking { replies.forEach { socket?.sendInteractive(it.toByteArray()) } }
                    }
                    override fun onState(state: ConnectionState) = Unit
                    override fun onOutput(text: String) { output.offer(text) }
                    override fun onError(message: String) {
                        val category = listOf("timeout", "timed out", "reset", "closed", "pong", "403", "401", "protocol").filter { message.contains(it, ignoreCase = true) }.joinToString(",").ifEmpty { "other" }
                        failures.offer(category)
                        println("LIVE ${uri.scheme}: socket failure category=$category")
                    }
                })
                socket!!.connect(resume, resume == null, attach)
                var screen = ""
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(50)
                while (System.nanoTime() < deadline) {
                    check(failures.isEmpty()) { "WebSocket connection failed" }
                    screen = (screen + output.poll(250, TimeUnit.MILLISECONDS).orEmpty()).takeLast(16000)
                    if (screen.contains("❯") || screen.contains("/help") && screen.contains("commands")) return
                }
                error("Hermes input prompt was not ready in 50 seconds")
            }

            phase = "new conversation and WebSocket readiness"
            connect(null)
            val marker = "CATGO_LIVE_${uri.scheme.uppercase()}_" + UUID.randomUUID().toString().take(8)
            val prompt = "$marker 连接测试。不要调用工具，只回复：连接正常。"
            println("LIVE ${uri.scheme}: terminal readiness PASS; vision=${model.capabilities?.supportsVision}")
            kotlinx.coroutines.delay(1500)
            assertTrue(socket!!.sendLine(prompt))
            phase = "first model reply"
            suspend fun waitReply(expected: String): List<HermesMessage> {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(110)
                var terminal = ""
                while (System.nanoTime() < deadline) {
                    Thread.sleep(1000)
                    while (true) { terminal += output.poll() ?: break }
                    if (sessionId == null) sessionId = app.getSessions().firstOrNull { it.preview.orEmpty().contains(marker) }?.id
                    val messages = sessionId?.let { app.getMessages(it) }.orEmpty()
                    if (messages.any { it.role == "assistant" && it.toolCalls.isNullOrEmpty() && it.content.contains(expected) }) return messages
                }
                println("LIVE ${uri.scheme}: reply diagnostic: connected=${socket?.isConnected()}, errors=${failures.size}, chars=${terminal.length}, inputEcho=${terminal.contains(marker)}, expectedText=${terminal.contains(expected)}, errorWord=${terminal.contains("error", ignoreCase = true)}, loginWord=${terminal.contains("login", ignoreCase = true)}")
                error("Requested model reply was not persisted in 110 seconds")
            }
            val initial = waitReply("连接正常")
            assertEquals(1, initial.count { it.role == "user" && it.content.contains(marker) })
            val originalId = requireNotNull(sessionId)
            println("LIVE ${uri.scheme}: new conversation, exact marked input and model reply PASS; session=$originalId")
            phase = "reconnect and resume"
            socket!!.close()
            connect(originalId)
            assertEquals(originalId, sessionId)
            val followup = "$marker 恢复测试。不要调用工具，只回复：恢复正常。"
            assertTrue(socket!!.sendLine(followup))
            phase = "resumed model reply"
            val resumed = waitReply("恢复正常")
            assertEquals(2, resumed.count { it.role == "user" && it.content.contains(marker) })
            assertTrue(resumed.any { it.role == "assistant" && it.content.contains("连接正常") })
            println("LIVE ${uri.scheme}: session resume and second model reply PASS; session=$originalId")
        } catch (error: Throwable) {
            // Avoid exception messages containing server response bodies, credentials or WS tickets.
            throw AssertionError("LIVE ${uri.scheme} failed at $phase; type=${error.javaClass.simpleName}; status=${(error as? HttpStatusException)?.statusCode}; session=$sessionId")
        } finally {
            socket?.close()
            settings.clear()
            cookieStorage.clear()
            credentialStorage.clear()
            ProxySelector.setDefault(previousProxy)
        }
    }
}
