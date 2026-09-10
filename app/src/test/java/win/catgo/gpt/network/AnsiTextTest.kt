package win.catgo.gpt.network

import org.junit.Assert.assertEquals
import org.junit.Test

class AnsiTextTest {
    @Test
    fun removesAnsiColorAndCarriageReturn() {
        val raw = "\u001B[32mHello\u001B[0m\r\nworld"

        assertEquals("Hello\nworld", AnsiText.clean(raw))
    }
}
