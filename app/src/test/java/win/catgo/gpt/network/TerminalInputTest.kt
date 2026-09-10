package win.catgo.gpt.network

import org.junit.Assert.*
import org.junit.Test

class TerminalInputTest {
    @Test fun contentAndEnterAreSeparateFrames() {
        assertEquals(listOf("\u0015\u001b[200~hello Hermes\u001b[201~", "\r"),
            TerminalInput.frames("  hello Hermes\n"))
    }
    @Test fun blankInputProducesNoFrames() {
        assertEquals(emptyList<String>(), TerminalInput.frames(" \n "))
    }
    @Test fun staleLineIsClearedWithoutDeletingLegitimateLetters() {
        val frames = TerminalInput.frames("lll 是合法输入")
        assertTrue(frames.first().startsWith("\u0015"))
        assertTrue(frames.first().contains("lll 是合法输入"))
        assertEquals("\r", frames.last())
    }
    @Test fun controlCharactersCannotCreateExtraCommands() {
        val frames = TerminalInput.frames("一\r/new\n二\u0003")
        assertEquals("\u0015\u001b[200~一 /new 二\u001b[201~", frames.first())
    }
}
