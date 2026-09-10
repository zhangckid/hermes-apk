package win.catgo.gpt.model

/** In-memory clarification transcript; passwords and secret prompts are never stored here. */
data class PromptExchange(val id: Long, val sessionKey: String, val afterMessages: Int,
    val question: String, val answer: String)
