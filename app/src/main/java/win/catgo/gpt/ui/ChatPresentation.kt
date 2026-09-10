package win.catgo.gpt.ui

import win.catgo.gpt.model.HermesMessage

/** Presentation and delivery rules independent of Compose and the PTY transport. */
internal object ChatPresentation {
    fun visible(messages: List<HermesMessage>): List<HermesMessage> =
        messages.filter { it.role == "user" ||
            (it.role == "assistant" && it.toolCalls.isNullOrEmpty() && it.content.isNotBlank()) }

    fun retainUntilReplacement(existing: List<HermesMessage>, incoming: List<HermesMessage>): List<HermesMessage> =
        incoming.ifEmpty { existing }

    fun prompt(messages: List<HermesMessage>, baseline: Long, expected: String): HermesMessage? =
        messages.firstOrNull { it.role == "user" && (it.id ?: 0) > baseline &&
            normalize(it.content) == normalize(expected) }

    fun answer(messages: List<HermesMessage>, userId: Long): HermesMessage? =
        messages.filter { (it.id ?: 0) > userId && it.role == "assistant" &&
            it.toolCalls.isNullOrEmpty() && it.content.isNotBlank() }.maxByOrNull { it.id ?: 0 }

    private fun normalize(value: String) = value.replace(Regex("\\s+"), " ").trim()
}
