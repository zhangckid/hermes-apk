package win.catgo.gpt.network

import win.catgo.gpt.i18n.t
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

interface HermesPtyBackend {
    val json: kotlinx.serialization.json.Json
    suspend fun createWebSocketTicket(): String
    fun openPtySocket(ticket: String, resumeSessionId: String?, fresh: Boolean,
        attachId: String, listener: WebSocketListener): WebSocket
}

class HermesPtySocket(
    private val client: HermesPtyBackend,
    private val listener: Listener,
) {
    @Volatile private var socket: WebSocket? = null
    private val isOpen = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private val inputMutex = Mutex()
    private var outputDecoder = TerminalOutputDecoder()
    private var utf8Decoder = Utf8StreamDecoder()

    suspend fun connect(resumeSessionId: String?, fresh: Boolean, attachId: String) {
        close()
        outputDecoder = TerminalOutputDecoder()
        utf8Decoder = Utf8StreamDecoder()
        val attempt = generation.incrementAndGet()
        listener.onState(ConnectionState.CONNECTING)
        val ticket = try { client.createWebSocketTicket() } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (isCurrent(attempt)) {
                listener.onError(error.message ?: t("聊天连接失败"))
                listener.onState(ConnectionState.CLOSED)
            }
            throw error
        }
        if (!isCurrent(attempt)) return

        val openedSocket = client.openPtySocket(
            ticket = ticket,
            resumeSessionId = resumeSessionId,
            fresh = fresh,
            attachId = attachId,
            listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!isCurrent(attempt)) {
                        webSocket.close(1000, "superseded")
                        return
                    }
                    isOpen.set(true)
                    webSocket.send("\u001b[RESIZE:90;32]")
                    listener.onState(ConnectionState.OPEN)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (isCurrent(attempt)) handleMessage(text, text.toByteArray(Charsets.UTF_8))
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (isCurrent(attempt)) {
                        val raw = bytes.toByteArray()
                        handleMessage(utf8Decoder.decode(raw), raw)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!isCurrent(attempt)) return
                    isOpen.set(false)
                    socket = null
                    listener.onState(if (code == 4410) ConnectionState.ENDED else ConnectionState.CLOSED)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!isCurrent(attempt)) return
                    isOpen.set(false)
                    socket = null
                    listener.onError(t.message ?: t("聊天连接失败"))
                    listener.onState(ConnectionState.CLOSED)
                }
            },
        )
        if (isCurrent(attempt)) {
            socket = openedSocket
        } else {
            openedSocket.close(1000, "superseded")
        }
    }

    suspend fun sendLine(text: String): Boolean = inputMutex.withLock {
        if (!isOpen.get()) return@withLock false
        val frames = TerminalInput.frames(text)
        if (frames.isEmpty()) return@withLock false
        val activeSocket = socket ?: return@withLock false
        if (!activeSocket.send(frames[0])) return@withLock false
        delay(INPUT_COMMIT_DELAY_MS)
        if (!isOpen.get() || socket !== activeSocket) return@withLock false
        activeSocket.send(frames[1])
    }

    /** Raw key input for the visible, current terminal. No Ctrl-U, paste wrapper or implicit Enter. */
    suspend fun sendInteractive(bytes: ByteArray, isCurrent: () -> Boolean = { true }): Boolean = inputMutex.withLock {
        if (!isCurrent() || !isOpen.get() || bytes.isEmpty() || bytes.size > 64 * 1024) return@withLock false
        socket?.send(bytes.toString(Charsets.UTF_8)) == true
    }

    fun resize(columns: Int, rows: Int): Boolean {
        if (columns !in 2..500 || rows !in 2..300) return false
        return isOpen.get() && socket?.send("\u001b[RESIZE:" + columns + ";" + rows + "]") == true
    }

    suspend fun sendImage(path: String): Boolean = sendLine("/image $path")

    fun commitLine(): Boolean = isOpen.get() && socket?.send("\r") == true

    fun stop(): Boolean = isOpen.get() && socket?.send("\u0003") == true

    fun close() {
        generation.incrementAndGet()
        isOpen.set(false)
        val activeSocket = socket
        socket = null
        activeSocket?.close(1000, "screen closed")
    }

    fun isConnected(): Boolean = isOpen.get()

    private fun isCurrent(attempt: Long): Boolean = generation.get() == attempt

    private fun handleMessage(raw: String, bytes: ByteArray) {
        val resumeId = runCatching {
            val objectValue = client.json.parseToJsonElement(raw).jsonObject
            if (objectValue["type"]?.jsonPrimitive?.content == "resume") {
                objectValue["id"]?.jsonPrimitive?.content
            } else null
        }.getOrNull()
        if (!resumeId.isNullOrBlank()) {
            listener.onSessionId(resumeId)
            return
        }
        listener.onRawOutput(bytes)
        outputDecoder.decode(raw).takeIf { it.isNotEmpty() }?.let(listener::onOutput)
    }

    interface Listener {
        fun onState(state: ConnectionState)
        fun onSessionId(sessionId: String)
        fun onOutput(text: String)
        fun onRawOutput(bytes: ByteArray) {}
        fun onError(message: String)
    }

    private companion object {
        const val INPUT_COMMIT_DELAY_MS = 100L
    }
}

object TerminalInput {
    fun frames(value: String): List<String> {
        val normalized = value.replace(Regex("[\\r\\n\\t]+"), " ")
            .filter { it >= ' ' && it != '\u007f' }.trim()
        // Clear a stale terminal edit buffer; paste bypasses CLI key bindings.
        // Do not strip visible letters: "lll" may be legitimate user input.
        return if (normalized.isEmpty()) emptyList() else
            listOf("\u0015\u001b[200~" + normalized + "\u001b[201~", "\r")
    }
}

enum class ConnectionState { IDLE, CONNECTING, OPEN, CLOSED, ENDED }

object AnsiText {
    private val controlSequence = Regex("\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))")
    private val remainingControls = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    fun clean(value: String): String = value
        .replace(controlSequence, "")
        .replace(remainingControls, "")
        .replace("\r", "")
}
