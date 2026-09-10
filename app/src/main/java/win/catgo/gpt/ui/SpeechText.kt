package win.catgo.gpt.ui

import win.catgo.gpt.i18n.t
/** Text normalization and safe chunking for Android's speech synthesizer. */
object SpeechText {
    fun fromMarkdown(markdown: String): String = markdown
        .replace(Regex("```[\\s\\S]*?```"), t(" 代码内容已省略。 "))
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), t(" 图片。 "))
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        .replace(Regex("[*_~>]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Extractive, bounded reading; the original detailed answer remains untouched. */
    fun concise(markdown: String, limit: Int = 280): String {
        require(limit >= 40)
        val text = fromMarkdown(markdown
            .replace(Regex("(?m)^\\s*\\|.*$"), "")
            .replace(Regex("https?://\\S+"), ""))
        if (text.length <= limit) return text
        val sentences = Regex("[^。！？.!?]+[。！？.!?]?").findAll(text)
            .map { it.value.trim() }.filter { it.isNotBlank() }.take(3).toList()
        val summary = sentences.joinToString(" ")
        if (summary.length <= limit - 14) return summary + t(" 详细内容请看文字。")
        val head = text.take(limit - 15)
        val boundary = head.lastIndexOfAny(charArrayOf('。', '！', '？', '，', ' ', '；'))
        return head.take(if (boundary >= 40) boundary + 1 else head.length).trim() +
            t("。详细内容请看文字。")
    }

    fun chunks(text: String, maxLength: Int): List<String> {
        require(maxLength > 0)
        val remaining = text.trim()
        if (remaining.isEmpty()) return emptyList()
        return buildList {
            var offset = 0
            while (offset < remaining.length) {
                val hardEnd = minOf(offset + maxLength, remaining.length)
                val end = if (hardEnd == remaining.length) {
                    hardEnd
                } else {
                    val boundary = remaining.lastIndexOfAny(
                        charArrayOf('。', '！', '？', '.', '!', '?', '，', ',', ' '),
                        startIndex = hardEnd - 1,
                    )
                    if (boundary >= offset + maxLength / 2) boundary + 1 else hardEnd
                }
                add(remaining.substring(offset, end).trim())
                offset = end
                while (offset < remaining.length && remaining[offset].isWhitespace()) offset++
            }
        }.filter(String::isNotEmpty)
    }
}
