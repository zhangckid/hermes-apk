package win.catgo.gpt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.SessionSummary
import win.catgo.gpt.network.ConnectionState

class ConversationStateTest {
    @Test
    fun `new chat clears the previous conversation immediately`() {
        val old = AppUiState(
            currentSessionId = "old-session",
            currentTitle = "Old title",
            messages = listOf(HermesMessage(role = "user", content = "old")),
            isGenerating = true,
            generatingConversationVersion = 4,
            liveActivity = "web_search",
            showReconnectPrompt = true,
            connectionError = "old connection failure",
            busy = true,
            conversationVersion = 4,
        )

        val fresh = ConversationState.newChat(old)

        assertNull(fresh.currentSessionId)
        assertNull(fresh.currentTitle)
        assertTrue(fresh.messages.isEmpty())
        assertFalse(fresh.isGenerating)
        assertNull(fresh.generatingConversationVersion)
        assertEquals("", fresh.liveActivity)
        assertFalse(fresh.showReconnectPrompt)
        assertNull(fresh.connectionError)
        assertFalse(fresh.busy)
        assertEquals(5, fresh.conversationVersion)
        assertTrue(fresh.draftSession)
    }

    @Test
    fun `late generating state from old conversation is ignored`() {
        val fresh = ConversationState.newChat(AppUiState(conversationVersion = 8))
        val staleUpdate = fresh.copy(
            isGenerating = true,
            generatingConversationVersion = 8,
        )

        assertFalse(ConversationState.isGenerating(staleUpdate))
    }

    @Test
    fun `late old conversation updates cannot mutate a fresh chat`() {
        val oldVersion = 12L
        val fresh = ConversationState.newChat(AppUiState(conversationVersion = oldVersion))

        val afterLateUpdates = (1..1_000).fold(fresh) { state, _ ->
            ConversationState.updateIfCurrent(state, oldVersion) {
                it.copy(
                    currentSessionId = "old-session",
                    messages = listOf(HermesMessage(role = "assistant", content = "late")),
                    isGenerating = true,
                    generatingConversationVersion = oldVersion,
                )
            }
        }

        assertEquals(fresh, afterLateUpdates)
    }

    @Test
    fun `reopening a running session keeps messages and kaomoji visible`() {
        val messages = listOf(HermesMessage(role = "user", content = "继续分析"))
        val activity = "─ (◔_◔) reflecting… · 41s │ gpt 5.6 sol"

        val opened = SessionPresentation.open(
            current = AppUiState(conversationVersion = 20),
            session = SessionSummary(id = "running-session", title = "分析"),
            cachedMessages = messages,
            cachedActivity = activity,
            isGenerating = true,
        )

        assertEquals(messages, opened.messages)
        assertEquals(activity, opened.liveActivity)
        assertTrue(opened.isGenerating)
        assertEquals(21L, opened.generatingConversationVersion)
        assertEquals(21L, opened.conversationVersion)
        assertEquals("running-session", opened.currentSessionId)
    }

    @Test
    fun `reconnect prompt stays hidden for the first ten seconds`() {
        assertFalse(ReconnectPromptPolicy.shouldShow(ConnectionState.CLOSED, 9_999))
        assertTrue(ReconnectPromptPolicy.shouldShow(ConnectionState.CLOSED, 10_000))
        assertFalse(ReconnectPromptPolicy.shouldShow(ConnectionState.OPEN, 10_000))
        assertFalse(ReconnectPromptPolicy.shouldShow(ConnectionState.ENDED, 60_000))
    }

    @Test
    fun `automatic reconnect backoff is bounded`() {
        assertEquals(250L, ReconnectPromptPolicy.retryDelay(0))
        assertEquals(500L, ReconnectPromptPolicy.retryDelay(1))
        assertEquals(1_000L, ReconnectPromptPolicy.retryDelay(2))
        assertEquals(2_000L, ReconnectPromptPolicy.retryDelay(20))
    }

    @Test
    fun `existing session polling never switches to an unrelated new session`() {
        val known = setOf("current-session", "older-session")

        assertFalse(SessionDiscoveryPolicy.shouldDiscover("current-session", known))
        assertTrue(SessionDiscoveryPolicy.shouldDiscover(null, known))
        assertTrue(SessionDiscoveryPolicy.shouldDiscover("fresh-resume-id", known))
    }

    @Test
    fun `unconfirmed delivery never blindly resends a user task`() {
        assertEquals(PromptDeliveryAction.NONE, PromptDeliveryPolicy.action(4, persisted = false))
        assertEquals(PromptDeliveryAction.NONE, PromptDeliveryPolicy.action(5, persisted = false))
        assertEquals(
            PromptDeliveryAction.NONE,
            PromptDeliveryPolicy.action(15, persisted = false),
        )
        assertEquals(PromptDeliveryAction.FAIL, PromptDeliveryPolicy.action(35, persisted = false))
    }

    @Test
    fun `persisted prompt never triggers delivery recovery`() {
        listOf(5, 15, 35, 299).forEach { attempt ->
            assertEquals(
                PromptDeliveryAction.NONE,
                PromptDeliveryPolicy.action(attempt, persisted = true),
            )
        }
    }

    @Test
    fun `generating indicator is shown only for current conversation`() {
        val state = AppUiState(
            isGenerating = true,
            generatingConversationVersion = 9,
            conversationVersion = 9,
        )

        assertTrue(ConversationState.isGenerating(state))
        assertFalse(ConversationState.isGenerating(state.copy(isGenerating = false)))
    }
}
