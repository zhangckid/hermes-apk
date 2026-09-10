package win.catgo.gpt.network

/** Small, bounded VT text screen for reading modal panels, not a shell UI. */
internal class TerminalTextScreen(private val columns: Int = 90, private val rows: Int = 32) {
    private val decoder = Utf8StreamDecoder()
    private var cells = Array(rows) { Array(columns) { " " } }
    private var x = 0
    private var y = 0
    private var savedX = 0
    private var savedY = 0
    private var top = 0
    private var bottom = rows - 1
    private var mode = 0
    private var sequence = ""
    private val replies = mutableListOf<String>()
    fun takeReplies(): List<String> = replies.toList().also { replies.clear() }
    private var wrapPending = false
    private var cursorVisible = true
    val completeFrame: Boolean get() = mode == 0 && cursorVisible
    var revision = 0L
        private set

    fun append(bytes: ByteArray) {
        revision++
        decoder.decode(bytes).codePoints().forEach { code ->
            when (mode) {
                0 -> when (code) {
                    27 -> mode = 1
                    13 -> { x = 0; wrapPending = false }
                    10, 11, 12 -> lineFeed()
                    8 -> { x = (x - 1).coerceAtLeast(0); wrapPending = false }
                    9 -> { x = ((x / 8 + 1) * 8).coerceAtMost(columns - 1); wrapPending = false }
                    else -> if (code >= 32 && code != 127) put(code)
                }
                1 -> {
                    mode = when (code) { 91 -> 2; 93, 80, 94, 95 -> 3; else -> 0 }
                    sequence = ""
                    when (code) {
                        55 -> { savedX = x; savedY = y }
                        56 -> { x = savedX; y = savedY }
                        99 -> { cells = Array(rows) { Array(columns) { " " } }; x = 0; y = 0 }
                        68 -> lineFeed()
                        69 -> { x = 0; lineFeed() }
                        77 -> if (y > top) y-- else { cells.copyInto(cells, top + 1, top, bottom); cells[top] = Array(columns) { " " } }
                    }
                }
                2 -> if (code in 64..126) { csi(code.toChar()); mode = 0 }
                    else if (sequence.length < 100) sequence += code.toChar() else { mode = 0; sequence = "" }
                3 -> when (code) { 7 -> mode = 0; 27 -> mode = 4 }
                4 -> mode = if (code == 92) 0 else 3
            }
        }
    }

    fun lines(): List<String> = cells.map { it.joinToString("").trimEnd() }

    private fun lineFeed() {
        wrapPending = false
        if (y == bottom) {
            cells.copyInto(cells, top, top + 1, bottom + 1)
            cells[bottom] = Array(columns) { " " }
        } else y = (y + 1).coerceAtMost(rows - 1)
    }

    private fun put(code: Int) {
        val type = Character.getType(code)
        val text = String(Character.toChars(code))
        if (type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() || code == 0x200d) {
            if (x > 0) cells[y][x - 1] += text
            return
        }
        val width = if (code in 0x1100..0x115f || code in 0x2e80..0xa4cf || code in 0xac00..0xd7a3 ||
            code in 0xf900..0xfaff || code in 0xfe10..0xfe6f || code in 0xff01..0xff60 ||
            code in 0xffe0..0xffe6 || code in 0x1f300..0x1faff || code in 0x20000..0x3ffff) 2 else 1
        if (wrapPending || x + width > columns) { x = 0; lineFeed() }
        cells[y][x] = text
        if (width == 2) cells[y][x + 1] = ""
        x += width
        if (x >= columns) { x = columns - 1; wrapPending = true }
    }

    private fun csi(final: Char) {
        val params = sequence.removePrefix("?").split(';').map { it.toIntOrNull() ?: 0 }
        fun p(index: Int = 0, default: Int = 1) = (params.getOrNull(index) ?: 0).let { if (it == 0) default else it }.coerceAtMost(10_000)
        fun erase(row: Int, from: Int = 0, until: Int = columns) { for (col in from until until) cells[row][col] = " " }
        when (final) {
            'A' -> y = (y - p()).coerceAtLeast(0)
            'B', 'e' -> y = (y + p()).coerceAtMost(rows - 1)
            'C', 'a' -> x = (x + p()).coerceAtMost(columns - 1)
            'D' -> x = (x - p()).coerceAtLeast(0)
            'E' -> { y = (y + p()).coerceAtMost(rows - 1); x = 0 }
            'F' -> { y = (y - p()).coerceAtLeast(0); x = 0 }
            'G', '`' -> x = (p() - 1).coerceIn(0, columns - 1)
            'd' -> y = (p() - 1).coerceIn(0, rows - 1)
            'H', 'f' -> { y = (p() - 1).coerceIn(0, rows - 1); x = (p(1) - 1).coerceIn(0, columns - 1) }
            'J' -> when (params[0]) {
                0 -> { erase(y, x); for (row in y + 1 until rows) erase(row) }
                1 -> { for (row in 0 until y) erase(row); erase(y, 0, x + 1) }
                2 -> for (row in 0 until rows) erase(row)
            }
            'K' -> when (params[0]) { 0 -> erase(y, x); 1 -> erase(y, 0, x + 1); 2 -> erase(y) }
            'X' -> erase(y, x, (x + p()).coerceAtMost(columns))
            'P' -> { val n = p().coerceAtMost(columns - x); cells[y].copyInto(cells[y], x, x + n, columns); erase(y, columns - n) }
            '@' -> { val n = p().coerceAtMost(columns - x); cells[y].copyInto(cells[y], x + n, x, columns - n); erase(y, x, x + n) }
            'L', 'M' -> if (y in top..bottom) {
                repeat(p().coerceAtMost(bottom - y + 1)) {
                    if (final == 'L') {
                        cells.copyInto(cells, y + 1, y, bottom); cells[y] = Array(columns) { " " }
                    } else {
                        cells.copyInto(cells, y, y + 1, bottom + 1); cells[bottom] = Array(columns) { " " }
                    }
                }
            }
            'S', 'T' -> repeat(p().coerceAtMost(bottom - top + 1)) {
                if (final == 'S') {
                    cells.copyInto(cells, top, top + 1, bottom + 1); cells[bottom] = Array(columns) { " " }
                } else {
                    cells.copyInto(cells, top + 1, top, bottom); cells[top] = Array(columns) { " " }
                }
            }
            'n' -> if (!sequence.startsWith("?") && replies.size < 8) {
                if (params[0] == 6) replies += "\u001b[" + (y + 1) + ";" + (x + 1) + "R"
                else if (params[0] == 5) replies += "\u001b[0n"
            }
            's' -> { savedX = x; savedY = y }
            'u' -> { x = savedX; y = savedY }
            'r' -> { top = (p() - 1).coerceIn(0, rows - 1); bottom = (p(1, rows) - 1).coerceIn(top, rows - 1); x = 0; y = 0 }
            'h', 'l' -> if (sequence.startsWith("?")) {
                if (25 in params) cursorVisible = final == 'h'
                if (1049 in params) { cells = Array(rows) { Array(columns) { " " } }; x = 0; y = 0 }
            }
        }
        if (final !in listOf('m', 'h', 'l', 'n', 'c')) wrapPending = false
    }
}
