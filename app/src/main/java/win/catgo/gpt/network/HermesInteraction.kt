package win.catgo.gpt.network

enum class HermesInteractionKind { QUESTION, APPROVAL, SECRET }

/**
 * Notification hints only, never authorization decisions. The original terminal remains
 * available for other server versions; no command/options/password are synthesized.
 */
class HermesInteractionDetector {
    private var tail = ""
    private var seenEnd = 0L
    private var offset = 0L
    fun append(text: String): HermesInteractionKind? {
        val combined = tail + text
        val base = offset - tail.length
        offset += text.length
        val candidates = MARKERS.flatMap { (kind, pattern) ->
            pattern.findAll(combined).map { Triple(base + it.range.last + 1, kind, it.value) }.toList()
        }
        tail = combined.takeLast(8192)
        val newest = candidates.filter { it.first > seenEnd }.maxByOrNull { it.first } ?: return null
        seenEnd = newest.first
        return newest.second
    }
    fun clear() { tail = ""; seenEnd = 0; offset = 0 }

    companion object {
        private val MARKERS = listOf(
            HermesInteractionKind.QUESTION to Regex("Hermes needs your input", RegexOption.IGNORE_CASE),
            HermesInteractionKind.APPROVAL to Regex("(?:⚠[\\uFE0F ]*Dangerous Command|\\[o\\]nce.{0,100}\\[d\\]eny)", RegexOption.IGNORE_CASE),
            HermesInteractionKind.SECRET to Regex("(?:Sudo Password Required|\\[sudo\\] password for [^\\r\\n:]{1,100}:|Skill Setup Required)", RegexOption.IGNORE_CASE),
        )
    }
}

/** Raw terminal history in memory only. Cleared on every socket/session replacement. */
class TerminalReplayBuffer(private val limit: Int = 128 * 1024) {
    private val chunks = ArrayDeque<ByteArray>()
    private var size = 0
    init { require(limit > 0) }
    fun append(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        // Keep chunk boundaries when possible; xterm's streaming decoder handles split UTF-8.
        val stored = if (bytes.size > limit) bytes.copyOfRange(bytes.size - limit, bytes.size) else bytes.copyOf()
        chunks.addLast(stored)
        size += stored.size
        while (size > limit && chunks.size > 1) {
            val removed = chunks.removeFirst()
            size -= removed.size
            removed.fill(0)
        }
    }
    fun snapshot(): List<ByteArray> = chunks.map { it.copyOf() }
    fun clear() {
        chunks.forEach { it.fill(0) }
        chunks.clear()
        size = 0
    }
}
