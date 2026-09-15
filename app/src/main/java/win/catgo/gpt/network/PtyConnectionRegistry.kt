package win.catgo.gpt.network

/** Transport retries retain the launch target, not the session ID learned from output.
 * Switching null -> resume after a new chat starts launches a second CLI owner on
 * Hermes Web. The browser retains its original resume query and channel instead.
 */
internal class PtyConnectionRegistry {
    data class Target(val channel: String, val resume: String?, val fresh: Boolean)
    private val targets = mutableMapOf<String, Target>()

    @Synchronized
    fun resolve(attach: String, resume: String?, fresh: Boolean): Target {
        targets[attach]?.let { return it.copy(fresh = false) }
        return Target("chat-$attach", resume?.takeIf { it.isNotBlank() }, fresh)
            .also { targets[attach] = it }
    }

    @Synchronized
    fun clear() = targets.clear()
}
