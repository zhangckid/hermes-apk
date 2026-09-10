package win.catgo.gpt.ui

import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.ModelInfo
import win.catgo.gpt.model.PickedImage
import win.catgo.gpt.model.ServerConfig
import win.catgo.gpt.model.SessionSummary
import win.catgo.gpt.network.ConnectionState

data class AppUiState(
    val destination: Destination = Destination.LOADING,
    val savedConfig: ServerConfig? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val sessions: List<SessionSummary> = emptyList(),
    val currentSessionId: String? = null,
    val currentTitle: String? = null,
    val messages: List<HermesMessage> = emptyList(),
    val pendingImages: List<PickedImage> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val showReconnectPrompt: Boolean = false,
    val ptyReady: Boolean = false,
    val isGenerating: Boolean = false,
    val generatingConversationVersion: Long? = null,
    val modelInfo: ModelInfo? = null,
    val modelOptions: win.catgo.gpt.model.ModelOptions? = null,
    val reasoning: String = "medium",
    val modelSettingsOpen: Boolean = false,
    val modelSettingsBusy: Boolean = false,
    val modelConfirmation: Boolean = false,
    val modelSettingsError: String? = null,
    val liveActivity: String = "",
    val prompt: win.catgo.gpt.network.HermesPrompt? = null,
    val promptBusy: Boolean = false,
    val promptHistory: List<win.catgo.gpt.model.PromptExchange> = emptyList(),
    val interactionKind: win.catgo.gpt.network.HermesInteractionKind? = null,
    val interactionReady: Boolean = false,
    val voiceRepliesEnabled: Boolean = false,
    val speechRequest: SpeechRequest? = null,
    val conversationVersion: Long = 0,
    val draftSession: Boolean = false,
)

data class SpeechRequest(val id: Long, val text: String)
