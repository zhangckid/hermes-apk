package win.catgo.gpt.network

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** PTY reads may split a multibyte character across websocket binary messages. */
internal class Utf8StreamDecoder {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    fun decode(bytes: ByteArray): String {
        val input = ByteBuffer.wrap(pending + bytes)
        val output = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, output, false)
        pending = ByteArray(input.remaining()).also { input.get(it) }
        output.flip()
        return output.toString()
    }
}
