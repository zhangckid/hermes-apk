package win.catgo.gpt.network

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class HermesInteractionTest {
    @Test fun detectsSplitQuestionsApprovalsAndSudoButNotOrdinarySpinnerOrTools() {
        val detector = HermesInteractionDetector()
        assertNull(detector.append("web search terminal sudo command output\n─ (◔_◔) reflecting…"))
        assertNull(detector.append("\nHermes needs your inp"))
        assertEquals(HermesInteractionKind.QUESTION, detector.append("ut\nWhich environment?\n"))
        assertNull(detector.append("1. Testing 2. Production\n"))
        assertEquals(HermesInteractionKind.APPROVAL, detector.append("\n⚠️  Dangerous Command\n"))
        assertEquals(HermesInteractionKind.SECRET, detector.append("\n🔐 Sudo Password Required\n"))
        detector.clear()
        assertNull(detector.append("ut\n"))
        assertEquals(HermesInteractionKind.APPROVAL, detector.append("[o]nce | [s]ession | [a]lways | [d]eny"))
    }

    @Test fun replayIsBoundedCopiedAndClearedBetweenConnections() {
        val replay = TerminalReplayBuffer(12)
        val source = "中文".toByteArray()
        replay.append(source)
        source.fill(0)
        assertEquals("中文", replay.snapshot().single().toString(Charsets.UTF_8))
        replay.append("1234567".toByteArray())
        assertEquals("1234567", replay.snapshot().single().toString(Charsets.UTF_8))
        replay.clear()
        assertTrue(replay.snapshot().isEmpty())
    }

    @Test(timeout = 20000) fun liveWebSocketCarriesInteractiveKeysWithoutNormalChatFramingOrAutoApproval() = runBlocking {
        val received = LinkedBlockingQueue<String>()
        val outputs = LinkedBlockingQueue<String>()
        val raw = LinkedBlockingQueue<ByteArray>()
        val ids = LinkedBlockingQueue<String>()
        val opened = CompletableDeferred<WebSocket>()
        val clientOpen = CompletableDeferred<Unit>()
        val server = MockWebServer()
        val http = OkHttpClient()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { opened.complete(ws) }
            override fun onMessage(ws: WebSocket, text: String) { received.offer(text) }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(code, reason) }
        }))
        server.start()
        val backend = object : HermesPtyBackend {
            override val json = Json { ignoreUnknownKeys = true }
            override suspend fun createWebSocketTicket() = "local-test-ticket"
            override fun openPtySocket(ticket: String, resumeSessionId: String?, fresh: Boolean,
                attachId: String, listener: WebSocketListener): WebSocket =
                http.newWebSocket(Request.Builder().url(server.url("/api/pty")).build(), listener)
        }
        val socket = HermesPtySocket(backend, object : HermesPtySocket.Listener {
            override fun onState(state: ConnectionState) { if (state == ConnectionState.OPEN) clientOpen.complete(Unit) }
            override fun onReplayStarted(terminalId: String) { ids.offer(terminalId) }
            override fun onOutput(text: String) { outputs.offer(text) }
            override fun onRawOutput(bytes: ByteArray) { raw.offer(bytes) }
            override fun onError(message: String) {}
        })
        suspend fun next(queue: LinkedBlockingQueue<String>) = withContext(Dispatchers.IO) {
            queue.poll(3, TimeUnit.SECONDS) ?: error("Expected frame")
        }
        try {
            socket.connect(null, true, "isolated-test")
            withTimeout(5000) { clientOpen.await() }
            val peer = withTimeout(5000) { opened.await() }
            assertEquals("\u001b[RESIZE:90;32]", next(received))
            peer.send("""{"type":"resume","id":"replayed-pty-id"}""")
            assertEquals("replayed-pty-id", next(ids))
            assertTrue(raw.isEmpty())
            val prompt = "\u001b[31m⚠️  Dangerous Command\u001b[0m\r\n1. Allow once\r\n2. Deny"
            peer.send(prompt)
            assertTrue(next(outputs).contains("Dangerous Command"))
            assertEquals(prompt, withContext(Dispatchers.IO) { raw.poll(3, TimeUnit.SECONDS) }!!.toString(Charsets.UTF_8))
            delay(200)
            assertTrue("Displaying an approval must never send an answer", received.isEmpty())
            assertTrue(socket.sendInteractive("\u001b[B\r".toByteArray()))
            assertEquals("\u001b[B\r", next(received))

            peer.send("Hermes needs your input\r\nChoose one")
            next(outputs)
            assertTrue(socket.sendInteractive("2".toByteArray()))
            assertEquals("2", next(received)) // No Ctrl-U, paste wrapper, or appended Enter.
            assertTrue(socket.sendInteractive("中文\r".toByteArray()))
            assertEquals("中文\r", next(received))
            peer.send("Sudo Password Required")
            next(outputs)
            assertTrue(socket.sendInteractive("local-fake-password\r".toByteArray()))
            assertEquals("local-fake-password\r", next(received))

            val normal = async { socket.sendLine("normal prompt") }
            assertEquals(TerminalInput.frames("normal prompt")[0], next(received))
            var leaseValid = true
            val staleApproval = async(start = CoroutineStart.UNDISPATCHED) {
                socket.sendInteractive("o\r".toByteArray()) { leaseValid }
            }
            leaseValid = false // Close/switch while waiting for normal input's commit.
            assertTrue(normal.await())
            assertEquals("\r", next(received))
            assertFalse(staleApproval.await())
            delay(150)
            assertTrue("Revoked input must not escape the send mutex", received.isEmpty())
            // New inline cards use the same websocket; selecting a number must not add
            // Enter (which could otherwise confirm the next approval that appears).
            val inlineCases = listOf(
                Triple(PromptFixtures.question(), "choice", "2"),
                Triple(PromptFixtures.openQuestion(), "text", "中文回答"),
                Triple(PromptFixtures.approval(), "choice", "4"),
                Triple(PromptFixtures.secret(), "text", "local-test-only"),
            )
            for ((panel, action, value) in inlineCases) {
                raw.clear()
                peer.send(panel)
                next(outputs)
                val bytes = withContext(Dispatchers.IO) { raw.poll(3, TimeUnit.SECONDS) }!!
                val screen = TerminalTextScreen()
                screen.append(bytes)
                val prompt = HermesPromptParser.parse(screen.lines())!!
                assertTrue(prompt.complete)
                assertTrue("Rendering a card must never grant permission", received.isEmpty())
                val answer = if (action == "choice") HermesPromptInput.choice(prompt, value)!!
                    else HermesPromptInput.text(prompt, value)!!
                assertTrue(socket.sendInteractive(answer))
                assertEquals(if (action == "choice") value else value + "\r", next(received))
            }
            assertFalse(socket.resize(0, 24))
            assertTrue(socket.resize(48, 18))
            assertEquals("\u001b[RESIZE:48;18]", next(received))
            socket.close()
            assertFalse(socket.sendInteractive("o\r".toByteArray()))
        } finally {
            socket.close()
            server.close()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }
}
