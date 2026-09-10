package win.catgo.gpt.network

import org.junit.Assert.*
import org.junit.Test

internal object PromptFixtures {
    fun panel(title: String, body: List<String>, hint: String, borderTitle: Boolean = false): String {
        val top = if (borderTitle) "╭─ $title ─╮" else "╭──────────────────────────────────────────────────────────────────╮"
        val rows = listOf(top) + (if (borderTitle) emptyList() else listOf("│ $title │")) +
            body.map { "│ $it │" } + listOf("╰──────────────────────────────────────────────────────────────────╯", hint)
        return "\u001b[2J\u001b[H" + rows.joinToString("\r\n")
    }
    fun question(text: String = "你希望部署到哪里？", multi: Boolean = false, seconds: Int = 300) =
        panel("Hermes needs your input", listOf(text, "", if (multi) "❯ [ ] 1. 测试环境" else "❯ 1. 测试环境",
            if (multi) "  [ ] 2. 生产环境" else "  2. 生产环境", if (multi) "  [ ] 3. Other (type your answer)" else "  3. Other (type your answer)"),
            "↑/↓ to select, Enter to confirm ($seconds" + "s)", true)
    fun openQuestion() = panel("Hermes needs your input", listOf("请用中文说明你的要求。", "Type your answer in the prompt below, then press Enter."),
        "type your answer and press Enter (300s)", true)
    fun approval(command: String = "sudo systemctl status hermes") = panel("⚠️  Dangerous Command",
        listOf(command, "", "❯ 1. Allow once", "  2. Allow for this session", "  3. Add to permanent allowlist", "  4. Deny", "  5. Show full command"),
        "↑/↓ to select, Enter to confirm (300s)")
    fun secret() = panel("🔐 Sudo Password Required", listOf("Enter password below (hidden), or press Enter to skip"),
        "password hidden · Enter to skip (45s)", true)
    fun parse(raw: String): HermesPrompt = TerminalTextScreen().let { screen ->
        screen.append(raw.toByteArray()); requireNotNull(HermesPromptParser.parse(screen.lines()))
    }
}

class HermesPromptTest {
    @Test fun boxedChineseQuestionBecomesTextAndExactChoices() {
        val prompt = PromptFixtures.parse(PromptFixtures.question())
        assertTrue(prompt.complete)
        assertTrue(prompt.canAnswerText)
        assertEquals("你希望部署到哪里？", prompt.body)
        assertEquals(listOf("测试环境", "生产环境", "Other (type your answer)"), prompt.choices.map { it.label })
        assertArrayEquals("2".toByteArray(), HermesPromptInput.choice(prompt, "2"))
        assertArrayEquals("3中文回答\r".toByteArray(), HermesPromptInput.text(prompt, "中文回答"))
    }

    @Test fun openQuestionUsesPlainReplyWithoutStartingANewChat() {
        val prompt = PromptFixtures.parse(PromptFixtures.openQuestion())
        assertTrue(prompt.freeText)
        assertTrue(prompt.canAnswerText)
        assertEquals("请用中文说明你的要求。", prompt.body)
        assertArrayEquals("我的要求\r".toByteArray(), HermesPromptInput.text(prompt, "我的要求"))
    }

    @Test fun approvalNeverAddsEnterOrExposesPermanentConsent() {
        val prompt = PromptFixtures.parse(PromptFixtures.approval())
        assertTrue(prompt.complete)
        assertEquals("sudo systemctl status hermes", prompt.body)
        assertArrayEquals("1".toByteArray(), HermesPromptInput.choice(prompt, "1"))
        assertArrayEquals("4".toByteArray(), HermesPromptInput.choice(prompt, "4"))
        assertNull(HermesPromptInput.choice(prompt, "2"))
        assertNull(HermesPromptInput.choice(prompt, "3"))
        assertNull(HermesPromptInput.text(prompt, "yes"))
    }

    @Test fun truncatedCommandCanBeExpandedOrDeniedButNeverAllowed() {
        val prompt = PromptFixtures.parse(PromptFixtures.approval("sudo … (choose Show full command)"))
        assertTrue(prompt.truncated)
        assertNull(HermesPromptInput.choice(prompt, "1"))
        assertArrayEquals("5".toByteArray(), HermesPromptInput.choice(prompt, "5"))
        assertNotNull(HermesPromptInput.choice(prompt, "4"))
    }

    @Test fun titlesInsideCommandTextCannotHideTheCommand() {
        val raw = PromptFixtures.approval("sudo echo 'Dangerous Command'")
        assertEquals("sudo echo 'Dangerous Command'", PromptFixtures.parse(raw).body)
        val duplicate = raw.replace("sudo echo 'Dangerous Command'", "sudo echo first │\r\n│ Dangerous Command")
        assertTrue(PromptFixtures.parse(duplicate).body.startsWith("sudo echo first"))
    }

    @Test fun malformedCommandRowsNeverBecomeAnActionableApproval() {
        val raw = PromptFixtures.approval().replace("│ sudo systemctl status hermes │", "│ sudo systemctl status hermes")
        val prompt = PromptFixtures.parse(raw)
        assertFalse(prompt.complete)
        assertNull(HermesPromptInput.choice(prompt, "1"))
    }

    @Test fun ordinaryToolLogsCannotBecomeAnApprovalPrompt() {
        assertNull(HermesPromptParser.parse(listOf("⚠️ Dangerous Command", "sudo example", "1. Allow once")))
        val raw = PromptFixtures.approval() + "\r\n╭─ Another modal ─╮\r\n│ Unknown request │\r\n╰─────────────────╯"
        val screen = TerminalTextScreen(); screen.append(raw.toByteArray())
        assertNull(HermesPromptParser.parse(screen.lines()))
    }

    @Test fun partialFramesMissingHintAndExpiredPromptsAreNotActionable() {
        val raw = PromptFixtures.question()
        val withoutEnd = raw.substringBefore("╰")
        assertFalse(PromptFixtures.parse(withoutEnd).complete)
        assertFalse(PromptFixtures.parse(raw.substringBefore("↑/↓")).complete)
        assertFalse(PromptFixtures.parse(PromptFixtures.question(seconds = 0)).complete)
    }

    @Test fun multiselectTogglesWithoutSubmittingUntilExplicitConfirmation() {
        val prompt = PromptFixtures.parse(PromptFixtures.question(multi = true))
        assertTrue(prompt.multiSelect)
        assertArrayEquals("1".toByteArray(), HermesPromptInput.choice(prompt, "1"))
        assertNull(HermesPromptInput.confirmMultiple(prompt))
        val checked = PromptFixtures.parse(PromptFixtures.question(multi = true).replace("[ ] 1.", "[x] 1."))
        assertArrayEquals(byteArrayOf(13), HermesPromptInput.confirmMultiple(checked))
        assertNull(HermesPromptInput.text(prompt, "do not bypass choices"))
    }

    @Test fun passwordUsesASeparateExplicitSubmissionAndHasADeadline() {
        val prompt = PromptFixtures.parse(PromptFixtures.secret())
        assertEquals(HermesInteractionKind.SECRET, prompt.kind)
        assertEquals(45, prompt.remainingSeconds)
        assertFalse(prompt.canAnswerText)
        assertArrayEquals("local-test-secret\r".toByteArray(), HermesPromptInput.text(prompt, "local-test-secret"))
        assertArrayEquals(byteArrayOf(13), HermesPromptInput.skipSecret(prompt))
        assertNull(HermesPromptInput.choice(prompt, "1"))
    }

    @Test fun controlCharactersAndUnknownKeysAreRejected() {
        val prompt = PromptFixtures.parse(PromptFixtures.openQuestion())
        for (text in listOf("yes\rgrant", "\u001b[3~", "a\u0000b", "a".repeat(16385))) {
            assertNull(HermesPromptInput.text(prompt, text))
        }
        assertNull(HermesPromptInput.choice(PromptFixtures.parse(PromptFixtures.question()), "9"))
        val bad = PromptFixtures.question().replace("2. 生产环境", "8. 生产环境")
        assertFalse(PromptFixtures.parse(bad).complete)
    }

    @Test fun batchActiveQuestionAndWrappedChoiceRemainReadable() {
        val raw = PromptFixtures.panel("Hermes needs your input", listOf("2 questions", "✓ 已回答的问题", "▸ 当前要回答的问题？",
            "  ❯ 1. First option", "      continued label", "    2. Second option", "    3. Other (type your answer)", "· 待回答的问题"),
            "↑/↓ to select, Enter to lock, Tab next question (300s)", true)
        val prompt = PromptFixtures.parse(raw)
        assertTrue(prompt.complete)
        assertTrue(prompt.body.contains("▸ 当前要回答的问题？"))
        assertEquals("First option continued label", prompt.choices.first().label)
    }

    @Test fun fragmentedUtf8AndAnsiProduceTheSamePanel() {
        val raw = PromptFixtures.question("保留中文与😀表情？")
        val screen = TerminalTextScreen()
        raw.toByteArray().forEach { screen.append(byteArrayOf(it)) }
        assertEquals(PromptFixtures.parse(raw), HermesPromptParser.parse(screen.lines()))
    }

    @Test fun cursorRedrawAndEraseReplaceTheOldQuestionWithoutLeftovers() {
        val screen = TerminalTextScreen()
        screen.append(PromptFixtures.question().toByteArray())
        screen.append("\u001b[2;1H\u001b[2K│ 新问题？ │".toByteArray())
        assertEquals("新问题？", HermesPromptParser.parse(screen.lines())!!.body)
        screen.append("\u001b[2J\u001b[Hnormal prompt ❯ ".toByteArray())
        assertNull(HermesPromptParser.parse(screen.lines()))
    }

    @Test fun cursorQueriesReturnOnlyFixedProtocolResponsesNotAnswers() {
        val screen = TerminalTextScreen()
        screen.append("\u001b[3;5H\u001b[6n\u001b[5n".toByteArray())
        assertEquals(listOf("\u001b[3;5R", "\u001b[0n"), screen.takeReplies())
        assertTrue(screen.takeReplies().isEmpty())
    }
}
