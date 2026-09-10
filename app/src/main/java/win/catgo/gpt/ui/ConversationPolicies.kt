package win.catgo.gpt.ui

import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.SessionSummary
import win.catgo.gpt.network.ConnectionState

internal object ReconnectPromptPolicy {
    fun shouldShow(connectionState: ConnectionState, disconnectedForMs: Long): Boolean =
        connectionState != ConnectionState.OPEN &&
            connectionState != ConnectionState.ENDED &&
            disconnectedForMs >= 10_000L

    fun retryDelay(attempt: Int): Long = when (attempt.coerceAtLeast(0)) {
        0 -> 250L
        1 -> 500L
        2 -> 1_000L
        else -> 2_000L
    }
}

internal object PtyReadiness {
    fun isReady(value: String): Boolean =
        value.contains("❯") ||
            value.contains("Try\"") ||
            value.contains("Try \"") ||
            (value.contains("/help") && value.contains("commands", ignoreCase = true))
}

internal object SessionDiscoveryPolicy {
    fun shouldDiscover(sessionId: String?, knownSessionIds: Set<String>): Boolean =
        sessionId == null || sessionId !in knownSessionIds
}

internal enum class PromptDeliveryAction { NONE, FAIL }

internal object PromptDeliveryPolicy {
    fun action(attempt: Int, persisted: Boolean): PromptDeliveryAction = when {
        persisted -> PromptDeliveryAction.NONE
        attempt >= 35 -> PromptDeliveryAction.FAIL
        else -> PromptDeliveryAction.NONE
    }
}

internal object SessionPresentation {
    fun open(
        current: AppUiState,
        session: SessionSummary,
        cachedMessages: List<HermesMessage>,
        cachedActivity: String,
        isGenerating: Boolean,
    ): AppUiState {
        val nextVersion = current.conversationVersion + 1
        return current.copy(
            busy = true,
            error = null,
            currentSessionId = session.id,
            currentTitle = session.title ?: session.preview,
            messages = cachedMessages,
            pendingImages = emptyList(),
            connectionState = ConnectionState.IDLE,
            showReconnectPrompt = false,
            liveActivity = cachedActivity,
            ptyReady = false,
            isGenerating = isGenerating,
            generatingConversationVersion = nextVersion.takeIf { isGenerating },
            speechRequest = null,
            conversationVersion = nextVersion,
            draftSession = false,
        )
    }
}

internal object ConversationState {
    fun newChat(current: AppUiState): AppUiState = current.copy(
        currentSessionId = null,
        currentTitle = null,
        messages = emptyList(),
        pendingImages = emptyList(),
        connectionState = ConnectionState.IDLE,
        showReconnectPrompt = false,
        ptyReady = false,
        isGenerating = false,
        generatingConversationVersion = null,
        liveActivity = "",
        error = null,
        busy = false,
        speechRequest = null,
        conversationVersion = current.conversationVersion + 1,
        draftSession = true,
    )

    fun isGenerating(state: AppUiState): Boolean =
        state.isGenerating &&
            state.generatingConversationVersion == state.conversationVersion

    inline fun updateIfCurrent(
        state: AppUiState,
        conversationVersion: Long,
        transform: (AppUiState) -> AppUiState,
    ): AppUiState = if (state.conversationVersion == conversationVersion) {
        transform(state)
    } else {
        state
    }
}
