package win.catgo.gpt.ui

import win.catgo.gpt.i18n.t
/** Extracts only the latest Hermes kaomoji status and hides PTY/tool details. */
internal object HermesLiveStatus {
    private val statusStart = Regex(
        """[─━—]\s*(?:\([^()\r\n]{2,32}\)|（[^（）\r\n]{2,32}）)""",
    )
    private val technicalDetail = Regex(
        """(?i)(search_files|terminal|web[_ -]?search|search_query|image_query|open_url|read_url|exec_command|tool call|calling tool|shell command|\bvoice\s+of(?:f)?\b|\bsessions?\s*\d|搜索网页|调用工具|运行工具)""",
    )

    fun extract(raw: String): String? = from(raw).takeUnless { it == t("Hermes 正在处理") }

    fun from(raw: String): String {
        val latest = statusStart.findAll(raw).lastOrNull() ?: return t("Hermes 正在处理")
        val lines = raw.substring(latest.range.first).lineSequence().iterator()
        val firstLine = if (lines.hasNext()) lines.next() else latest.value
        val continuation = if (lines.hasNext()) {
            lines.next().trim().takeIf { it.startsWith("·") || it.startsWith("│") }
        } else {
            null
        }
        return listOfNotNull(firstLine, continuation)
            .joinToString(" ")
            .substringBeforeTechnicalDetail()
            .replace(Regex("[ \t]+"), " ")
            .trim()
            .take(140)
            .ifBlank { t("Hermes 正在处理") }
    }

    private fun String.substringBeforeTechnicalDetail(): String {
        val detail = technicalDetail.find(this) ?: return this
        return substring(0, detail.range.first).trimEnd(' ', '·', '│', '|')
    }
}

/** Keep the last complete face across log bursts and fragmented terminal frames. */
internal class LiveStatusAccumulator {
    private var buffer = ""
    var latest: String = ""
        private set

    fun append(chunk: String): String {
        buffer = (buffer + chunk).takeLast(8_000)
        HermesLiveStatus.extract(buffer)?.let { latest = it }
        return latest
    }

    fun reset(previous: String = "") {
        buffer = ""
        latest = previous
    }
}
