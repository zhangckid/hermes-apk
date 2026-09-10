package win.catgo.gpt.network

/** ANSI sequences may span websocket messages. Never expose their final letters (e.g. l). */
internal class TerminalOutputDecoder {
    private enum class Mode { TEXT, ESCAPE, CSI, OSC, OSC_ESCAPE }
    private var mode = Mode.TEXT
    fun decode(value: String): String = buildString {
        value.forEach { ch ->
            when (mode) {
                Mode.TEXT -> when {
                    ch == '\u001b' -> mode = Mode.ESCAPE
                    ch == '\r' -> append('\n')
                    ch == '\n' || ch == '\t' || ch >= ' ' && ch != '\u007f' -> append(ch)
                }
                Mode.ESCAPE -> mode = when (ch) {
                    '[' -> Mode.CSI
                    ']' -> Mode.OSC
                    else -> Mode.TEXT
                }
                Mode.CSI -> if (ch in '@'..'~') mode = Mode.TEXT
                Mode.OSC -> when (ch) {
                    '\u0007' -> mode = Mode.TEXT
                    '\u001b' -> mode = Mode.OSC_ESCAPE
                }
                Mode.OSC_ESCAPE -> mode = if (ch == '\\') Mode.TEXT else Mode.OSC
            }
        }
    }
}
