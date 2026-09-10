package win.catgo.gpt.network

data class HermesPromptChoice(val key: String, val label: String, val checked: Boolean = false)
data class HermesPrompt(
    val id: Long = 0,
    val kind: HermesInteractionKind,
    val body: String,
    val choices: List<HermesPromptChoice>,
    val freeText: Boolean,
    val multiSelect: Boolean,
    val complete: Boolean,
    val truncated: Boolean,
    val remainingSeconds: Int? = null,
) {
    val canAnswerText get() = complete && !truncated && kind == HermesInteractionKind.QUESTION &&
        (freeText || !multiSelect && choices.any { it.label.startsWith("Other (") })
}

/** Only known complete boxed CLI panels become actionable. No tool-output guesses authorize commands. */
internal object HermesPromptParser {
    private val choice = Regex("""^\s*[❯>]?\s*(?:\[([ xX])]\s*)?([1-9]|0)\.\s+(.+)$""")
    private val titles = listOf("Hermes needs your input" to HermesInteractionKind.QUESTION,
        "Dangerous Command" to HermesInteractionKind.APPROVAL, "Sudo Password Required" to HermesInteractionKind.SECRET,
        "Skill Setup Required" to HermesInteractionKind.SECRET)
    fun parse(lines: List<String>): HermesPrompt? {
        fun topBorder(row: String) = row.trimStart().let { it.startsWith("╭") || it.startsWith("┌") }
        fun title(row: String) = row.trim().trim('│', '┃', '╭', '╮', '┌', '┐', '─', '━', ' ')
            .removePrefix("⚠️").removePrefix("⚠").removePrefix("🔐").removePrefix("🔑").trim()
        val top = lines.indexOfLast(::topBorder)
        if (top < 0) return null
        val start = (top..minOf(top + 1, lines.lastIndex)).firstOrNull { index ->
            titles.any { title(lines[index]) == it.first }
        } ?: return null
        val kind = titles.first { title(lines[start]) == it.first }.second
        // A title printed as ordinary text must not masquerade as an interactive panel.
        if (lines[start].none { it in "│┃╭┌" }) return null
        val end = (start + 1 until lines.size).firstOrNull {
            lines[it].trimStart().let { row -> (row.startsWith("╰") && row.endsWith("╯")) || (row.startsWith("└") && row.endsWith("┘")) }
        }
        val panelRows = lines.subList(start + 1, end ?: lines.size)
        val wellFormed = panelRows.all { row ->
            val value = row.trim()
            value.length >= 2 && value.first() in "│┃" && value.last() in "│┃"
        }
        val contents = panelRows.takeWhile { it.contains('│') || it.contains('┃') }
            .map { row -> val left = row.indexOfFirst { it == '│' || it == '┃' }; val right = row.indexOfLast { it == '│' || it == '┃' }
                if (right > left) row.substring(left + 1, right).trimEnd() else "" }
        val options = mutableListOf<HermesPromptChoice>()
        val body = mutableListOf<String>()
        var inChoices = false
        var multi = false
        var unsupportedChoice = false
        for (row in contents) {
            if (Regex("""^\s*[❯>]?\s*(?:\[[ xX]]\s*)?(?:\d{2,}|)\.\s+""").containsMatchIn(row)) unsupportedChoice = true
            val match = choice.matchEntire(row)
            if (match != null) {
                inChoices = true
                multi = multi || match.groups[1] != null
                options += HermesPromptChoice(match.groupValues[2], match.groupValues[3], match.groupValues[1].equals("x", true))
            } else if (!inChoices && !row.trim().startsWith("Type your answer in the prompt below")) body += row.removePrefix(" ")
            else if (row.isNotBlank() && row.takeWhile { it == ' ' }.length >= 4 && options.isNotEmpty()) {
                val old = options.removeAt(options.lastIndex)
                options += old.copy(label = old.label + " " + row.trim())
            }
        }
        val hint = if (end != null) lines.drop(end + 1).joinToString("\n") else ""
        val active = when (kind) {
            HermesInteractionKind.QUESTION -> hint.contains("type your answer", true) || Regex("↑/↓.*Enter").containsMatchIn(hint)
            HermesInteractionKind.APPROVAL -> Regex("↑/↓.*Enter").containsMatchIn(hint)
            HermesInteractionKind.SECRET -> hint.contains("password hidden", true) || hint.contains("secret hidden", true)
        }
        val knownOptions = !unsupportedChoice && options.size <= 10 && options.map { it.key } ==
            (1..options.size).map { (it % 10).toString() }
        val text = body.joinToString("\n").trim('\n')
        val truncated = Regex("""(?i)(truncated|choose Show full command|\.\.\.$|…$)""").containsMatchIn(text)
        return HermesPrompt(kind = kind, body = text, choices = options,
            freeText = hint.contains("type your answer", true) || options.any { it.label.startsWith("Other (type below)") },
            multiSelect = multi, complete = end != null && wellFormed && active && text.isNotBlank() && knownOptions &&
                !Regex("\\(0s\\)").containsMatchIn(hint) && (kind != HermesInteractionKind.APPROVAL || options.isNotEmpty()),
            truncated = truncated,
            remainingSeconds = Regex("""\((\d{1,6})s\)""").find(hint)?.groupValues?.get(1)?.toIntOrNull())
    }
}

/** Exact input for supported CLI handlers. In particular, numeric choices must NOT append Enter. */
internal object HermesPromptInput {
    fun text(prompt: HermesPrompt, value: String): ByteArray? {
        if (!prompt.complete || prompt.truncated || value.isBlank() || value.toByteArray().size > 16_384 ||
            value.any { it.isISOControl() || it == '\u2028' || it == '\u2029' }) return null
        if (prompt.kind == HermesInteractionKind.APPROVAL) return null
        val prefix = if (prompt.kind == HermesInteractionKind.SECRET || prompt.freeText) "" else {
            if (prompt.multiSelect) return null
            prompt.choices.firstOrNull { it.label.startsWith("Other (") }?.key ?: return null
        }
        return (prefix + value + "\r").toByteArray(Charsets.UTF_8)
    }

    fun choice(prompt: HermesPrompt, key: String): ByteArray? {
        if (!prompt.complete || prompt.freeText || prompt.kind == HermesInteractionKind.SECRET) return null
        val option = prompt.choices.firstOrNull { it.key == key } ?: return null
        if (prompt.kind == HermesInteractionKind.APPROVAL) {
            if (option.label !in listOf("Allow once", "Deny", "Show full command")) return null
            if (prompt.truncated && option.label == "Allow once") return null
        } else if (prompt.truncated) return null
        return key.toByteArray(Charsets.UTF_8)
    }

    fun confirmMultiple(prompt: HermesPrompt): ByteArray? =
        if (prompt.complete && !prompt.truncated && prompt.multiSelect && !prompt.freeText &&
            prompt.kind == HermesInteractionKind.QUESTION && prompt.choices.any { it.checked }) byteArrayOf(13) else null

    fun skipSecret(prompt: HermesPrompt): ByteArray? =
        if (prompt.complete && prompt.kind == HermesInteractionKind.SECRET) byteArrayOf(13) else null
}
