package win.catgo.gpt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySummaryTest {
    @Test
    fun screenshotFooterGarbageIsNotAThinkingStatus() {
        assertEquals("Hermes 正在处理", HermesLiveStatus.from(
            "? 8920s | voice of | session12345678910s | voice of | session12345678920"))
        assertEquals("─ (◔_◔) reflecting… · 41s │ gpt 5.6 sol", HermesLiveStatus.from(
            "─ (◔_◔) reflecting… · 41s │ gpt 5.6 sol | voice off | session123456789"))
        assertEquals("─ (◔_◔) reflecting… · 41s", HermesLiveStatus.from(
            "─ (◔_◔) reflecting… · 41s | session123456789"))
    }

    @Test
    fun `shows the latest Hermes kaomoji status`() {
        val raw = "─ (•‿•) thinking… · 12s │ old model\n─ (◔_◔) reflecting…\n  · 41s │ gpt 5.6 sol l"

        assertEquals(
            "─ (◔_◔) reflecting… · 41s │ gpt 5.6 sol l",
            HermesLiveStatus.from(raw),
        )
    }

    @Test
    fun `ordinary parentheses are not mistaken for a Hermes face`() {
        assertEquals(
            "Hermes 正在处理",
            HermesLiveStatus.from("exec_command run(function_call)\nweb_search(query)"),
        )
    }

    @Test
    fun `web search and tool details are never shown`() {
        val raw = "web_search query\nCalling tool search_query\nexec_command"

        assertEquals("Hermes 正在处理", HermesLiveStatus.from(raw))
    }

    @Test
    fun `technical detail after a status is removed`() {
        val raw = "─ (◔_◔) reflecting… · 8s │ gpt 5.6 sol │ web_search query"

        assertEquals("─ (◔_◔) reflecting… · 8s │ gpt 5.6 sol", HermesLiveStatus.from(raw))
    }

    @Test
    fun `full width kaomoji is supported`() {
        assertEquals("━（・_・） 思考中 · 3s", HermesLiveStatus.from("━（・_・） 思考中 · 3s"))
    }

    @Test
    fun `Hermes prompt marks terminal ready`() {
        assertTrue(PtyReadiness.isReady("❯ Try \"fix the linter errors\""))
        assertTrue(PtyReadiness.isReady("tools · skills · /help for commands"))
    }

    @Test
    fun `startup text alone is not terminal ready`() {
        assertFalse(PtyReadiness.isReady("starting agent… forging session"))
    }
}
