package win.catgo.gpt.ui

import org.junit.Assert.*
import org.junit.Test
import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.ToolCall
import win.catgo.gpt.network.TerminalOutputDecoder

class ChatRegressionTest {
    @Test fun chineseAndEmojiSurviveEveryBinaryFrameBoundary() {
        val text = "中文 ─ (◔_◔) reflecting… 😀"
        val bytes = text.toByteArray()
        for (split in 0..bytes.size) {
            val decoder = win.catgo.gpt.network.Utf8StreamDecoder()
            assertEquals(text, decoder.decode(bytes.copyOfRange(0, split)) +
                decoder.decode(bytes.copyOfRange(split, bytes.size)))
        }
    }
    @Test fun splitEscapeSequencesDoNotLeakLIntoText() {
        val decoder = TerminalOutputDecoder()
        assertEquals("", decoder.decode("\u001b[?2004"))
        assertEquals("", decoder.decode("l\u001b[?25"))
        assertEquals("lll 是我输入的", decoder.decode("llll 是我输入的"))
    }
    @Test fun everyAnsiSplitProducesTheSameVisibleText() {
        val raw = "\u001b[?2004l\u001b[32m你好\u001b[0m"
        for (split in 0..raw.length) {
            val decoder = TerminalOutputDecoder()
            assertEquals("你好", decoder.decode(raw.take(split)) + decoder.decode(raw.drop(split)))
        }
    }
    @Test fun longToolBurstDoesNotEraseLastFace() {
        val status = LiveStatusAccumulator()
        val face = "─ (◔_◔) reflecting… · 41s │ model"
        status.append(face + "\n")
        repeat(500) { status.append("terminal search_files web_search\n") }
        assertEquals(face, status.latest)
    }
    @Test fun fragmentedFaceAndReconnectKeepPreviousValidStatus() {
        val status = LiveStatusAccumulator()
        status.reset("─ (◔_◔) reflecting… · 41s")
        status.append("─ (•")
        assertEquals("─ (◔_◔) reflecting… · 41s", status.latest)
        status.append("‿•) thinking… · 42s\n")
        assertEquals("─ (•‿•) thinking… · 42s", status.latest)
        status.reset(status.latest)
        status.append("startup\n")
        assertEquals("─ (•‿•) thinking… · 42s", status.latest)
    }
    @Test fun emptyReconnectSnapshotDoesNotBlankHistory() {
        val history = listOf(HermesMessage(1, "user", "保留内容"))
        assertEquals(history, ChatPresentation.retainUntilReplacement(history, emptyList()))
        val replacement = history + HermesMessage(2, "assistant", "新内容")
        assertEquals(replacement, ChatPresentation.retainUntilReplacement(history, replacement))
    }
    @Test fun hundredsOfToolsDoNotCreateChatRows() {
        val user = HermesMessage(1, "user", "问题")
        val answer = HermesMessage(900, "assistant", "完整回答")
        val tools = (2L..800L).map { HermesMessage(it, "tool", "large output") }
        val intermediate = HermesMessage(801, "assistant", "Searching…", listOf(ToolCall()))
        assertEquals(listOf(user, answer), ChatPresentation.visible(listOf(user) + tools + intermediate + answer))
    }
    @Test fun repeatedPromptMustBelongToThisTurn() {
        val old = listOf(HermesMessage(1, "user", "你好"), HermesMessage(2, "assistant", "旧回答"))
        assertNull(ChatPresentation.prompt(old, 2, "你好"))
        assertNotNull(ChatPresentation.prompt(old + HermesMessage(3, "user", "你好"), 2, "你好"))
        assertNull(ChatPresentation.prompt(listOf(HermesMessage(3, "user", "你好别的问题")), 2, "你好"))
    }
    @Test fun intermediateToolCallIsNotAFinalAnswer() {
        val messages = listOf(HermesMessage(3, "assistant", "我来搜索", listOf(ToolCall())),
            HermesMessage(4, "tool", "result"))
        assertNull(ChatPresentation.answer(messages, 2))
        assertEquals(5L, ChatPresentation.answer(messages + HermesMessage(5, "assistant", "最终回答"), 2)?.id)
    }
    @Test fun speechIsConciseButDetailedMessageIsUnchanged() {
        val detailed = "第一项已经完成。第二项还要确认。" + "这里是详细的步骤说明。".repeat(80)
        val message = HermesMessage(1, "assistant", detailed)
        val spoken = SpeechText.concise(message.content)
        assertTrue(spoken.length <= 280)
        assertTrue(spoken.startsWith("第一项已经完成"))
        assertEquals(detailed, message.content)
        assertFalse(SpeechText.concise("结论。\n| 大表格 | 数据 |\nhttps://example.org").contains("大表格"))
    }
}
