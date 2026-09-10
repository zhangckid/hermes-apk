package win.catgo.gpt.network

/** Per-socket request identity and send leases; stale/repeated UI events cannot answer a new prompt. */
internal class HermesPromptTracker(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    data class Lease(val id: Long, val revision: Long)
    private var screen = TerminalTextScreen()
    private var serial = 0L
    private var deadline: Long = Long.MAX_VALUE
    private var activeLease: Lease? = null
    private fun identity(prompt: HermesPrompt?) = prompt?.copy(id = 0, remainingSeconds = null)
    private var submitted: HermesPrompt? = null
    var current: HermesPrompt? = null
        private set
    var ready = false
        private set
    var busy = false
        private set

    fun takeReplies() = screen.takeReplies()
    fun append(bytes: ByteArray) { screen.append(bytes); ready = false }
    fun refresh() {
        val parsed = HermesPromptParser.parse(screen.lines())
        if (parsed == null) { current = null; submitted = null; ready = false; busy = false; return }
        val previous = current
        val same = identity(previous) == identity(parsed)
        current = parsed.copy(id = if (same) previous!!.id else ++serial)
        val nextDeadline = parsed.remainingSeconds?.let { clock() + it * 1000L } ?: Long.MAX_VALUE
        deadline = if (same) minOf(deadline, nextDeadline) else nextDeadline
        busy = activeLease != null || submitted == identity(parsed)
        ready = parsed.complete && !busy && screen.completeFrame
    }
    fun begin(id: Long): Lease? {
        if (!ready || busy || clock() >= deadline || current?.id != id) return null
        ready = false
        busy = true
        return Lease(id, screen.revision).also { activeLease = it }
    }
    fun valid(lease: Lease) = activeLease == lease && current?.id == lease.id &&
        screen.revision == lease.revision && busy && clock() < deadline
    fun sent(lease: Lease) {
        if (activeLease == lease) { activeLease = null; submitted = identity(current); ready = false; busy = true }
    }
    fun failed(lease: Lease) {
        if (activeLease == lease) { activeLease = null; busy = false; submitted = null; refresh() }
    }
    fun clear() {
        screen = TerminalTextScreen()
        serial++
        activeLease = null
        deadline = Long.MAX_VALUE
        current = null; submitted = null; ready = false; busy = false
    }
}
