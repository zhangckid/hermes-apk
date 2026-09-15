package win.catgo.gpt.ui

import win.catgo.gpt.i18n.t
import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import win.catgo.gpt.AppContainer
import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.ModelInfo
import win.catgo.gpt.model.PickedImage
import win.catgo.gpt.model.ServerConfig
import win.catgo.gpt.model.SessionSummary
import win.catgo.gpt.network.ConnectionState
import win.catgo.gpt.network.HermesClient
import win.catgo.gpt.network.HermesPtySocket
import win.catgo.gpt.network.HermesInteractionKind
import win.catgo.gpt.network.HermesPrompt
import win.catgo.gpt.network.HermesPromptInput
import win.catgo.gpt.network.HermesPromptTracker
import win.catgo.gpt.model.PromptExchange

private data class RunningRequest(
    val baselineId: Long,
    val expectedPrompt: String,
    val knownSessionIds: Set<String>,
)

enum class Destination { LOADING, LOGIN, CHAT }

class AppViewModel(
    application: Application,
    private val client: HermesClient,
    private val attachIdForConnection: (Boolean) -> String,
) : AndroidViewModel(application), HermesPtySocket.Listener {
    private val _state = MutableStateFlow(
        AppUiState(
            savedConfig = client.savedConfig(),
            voiceRepliesEnabled = client.voiceRepliesEnabled(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    @Volatile private var pty: HermesPtySocket? = null
    @Volatile private var conversationAttachId = attachIdForConnection(false)
    private var accountVersion = 0L
    private var authenticationJob: Job? = null
    private var pollJob: Job? = null
    private var navigationJob: Job? = null
    private var sendJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectPromptJob: Job? = null
    private val sessionMessages = ConcurrentHashMap<String, List<HermesMessage>>()
    private val sessionActivities = ConcurrentHashMap<String, String>()
    private val runningRequests = ConcurrentHashMap<String, RunningRequest>()
    @Volatile private var unboundRunningRequest: RunningRequest? = null
    private var nextSpeechRequestId = 1L
    private val attachIds = mutableMapOf<String, String>()
    private val liveStatus = LiveStatusAccumulator()
    private var readinessBuffer = ""
    private var modelJob: Job? = null
    private var backgroundSince: Long? = null
    private val promptTracker = HermesPromptTracker()
    private var promptRefresh: Job? = null
    private var promptWriter: Job? = null
    private var interactionRefresh: Job? = null

    init {
        restoreSession()
    }

    fun login(config: ServerConfig, password: String) {
        val validation = config.validate()
        if (validation != null) {
            showError(validation)
            return
        }
        if (password.isBlank()) {
            showError(t("请输入密码"))
            return
        }
        authenticationJob?.cancel()
        val owner = ++accountVersion
        authenticationJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                withTimeout(30_000) {
                    client.login(config, password)
                    sessionMessages.clear()
                    sessionActivities.clear()
                    runningRequests.clear()
                    attachIds.clear()
                    loadHome(owner)
                }
            }.onFailure { error ->
                if (owner != accountVersion) return@onFailure
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                _state.update {
                    it.copy(
                        destination = Destination.LOGIN,
                        savedConfig = config,
                        busy = false,
                        error = loginFailureMessage(error),
                    )
                }
            }
        }
    }

    fun newChat() {
        resetInteraction()
        rememberCurrentSessionDisplay()
        unboundRunningRequest = null
        cancelReconnect()
        navigationJob?.cancel()
        sendJob?.cancel()
        pollJob?.cancel()
        pty?.close()
        pty = null
        conversationAttachId = attachIdForConnection(true)
        _state.update(ConversationState::newChat)
        val conversationVersion = _state.value.conversationVersion
        navigationJob = viewModelScope.launch {
            runCatching {
                connectSocket(
                    sessionId = null,
                    fresh = true,
                    conversationVersion = conversationVersion,
                )
            }
                .onFailure { error ->
                    if (error !is CancellationException && isCurrentConversation(conversationVersion)) {
                        updateConversation(conversationVersion) { it.copy(connectionState = ConnectionState.CLOSED) }
                        startAutoReconnect()
                    }
                }
        }
    }

    fun openSession(session: SessionSummary) {
        resetInteraction()
        rememberCurrentSessionDisplay()
        unboundRunningRequest = null
        conversationAttachId = attachIds.getOrPut(session.id) { java.util.UUID.randomUUID().toString() }
        val cachedMessages = sessionMessages[session.id].orEmpty()
        val cachedActivity = sessionActivities[session.id].orEmpty()
        val runningRequest = runningRequests[session.id]
        cancelReconnect()
        navigationJob?.cancel()
        navigationJob = viewModelScope.launch {
            sendJob?.cancel()
            pollJob?.cancel()
            pty?.close()
            pty = null
            _state.update { current ->
                SessionPresentation.open(
                    current = current,
                    session = session,
                    cachedMessages = cachedMessages,
                    cachedActivity = cachedActivity,
                    isGenerating = runningRequest != null,
                )
            }
            val conversationVersion = _state.value.conversationVersion
            runCatching {
                val messages = ChatPresentation.retainUntilReplacement(
                    cachedMessages, client.getMessages(session.id))
                sessionMessages[session.id] = messages
                updateConversation(conversationVersion) { it.copy(messages = messages, busy = false) }
                connectSocket(session.id, fresh = false, conversationVersion = conversationVersion)
                if (runningRequest != null) {
                    startPollingForReply(
                        baselineId = runningRequest.baselineId,
                        expectedPrompt = runningRequest.expectedPrompt,
                        knownSessionIds = runningRequest.knownSessionIds,
                        conversationVersion = conversationVersion,
                    )
                }
            }.onFailure { error ->
                if (error !is CancellationException && isCurrentConversation(conversationVersion)) {
                    updateConversation(conversationVersion) {
                        it.copy(busy = false, connectionState = ConnectionState.CLOSED)
                    }
                    startAutoReconnect()
                    if (runningRequest != null) startPollingForReply(runningRequest.baselineId,
                        runningRequest.expectedPrompt, runningRequest.knownSessionIds, conversationVersion)
                }
            }
        }
    }

    fun sendMessage(text: String): Boolean {
        _state.value.prompt?.let { prompt ->
            if (prompt.kind != HermesInteractionKind.QUESTION || _state.value.pendingImages.isNotEmpty()) return false
            return answerPrompt(prompt.id, text)
        }
        val trimmed = text.trim()
        val images = _state.value.pendingImages
        if (trimmed.isBlank() && images.isEmpty()) return false
        if (ConversationState.isGenerating(_state.value) || _state.value.modelSettingsBusy ||
            _state.value.interactionKind != null) return false

        val conversationVersion = _state.value.conversationVersion
        _state.value.currentSessionId?.let(sessionActivities::remove)
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            if (!isCurrentConversation(conversationVersion)) return@launch
            val baselineId = _state.value.messages.mapNotNull { it.id }.maxOrNull() ?: 0L
            val knownSessionIds = _state.value.sessions.map { it.id }.toSet()
            val actualPrompt = trimmed.ifBlank { t("请分析我发送的图片") }
            val pending = HermesMessage(
                role = "user",
                content = actualPrompt,
                timestamp = System.currentTimeMillis() / 1000.0,
                pending = true,
            )
            updateConversation(conversationVersion) {
                it.copy(
                    messages = it.messages + pending,
                    pendingImages = emptyList(),
                    isGenerating = true,
                    generatingConversationVersion = conversationVersion,
                    liveActivity = "",
                    error = null,
                )
            }

            liveStatus.reset()
            rememberRunningRequest(RunningRequest(baselineId, actualPrompt, knownSessionIds))
            runCatching {
                ensureSocket(conversationVersion)
                images.forEach { image ->
                    if (!isCurrentConversation(conversationVersion)) return@launch
                    val path = client.uploadImage(image)
                    if (!isCurrentConversation(conversationVersion)) return@launch
                    if (pty?.sendImage(path) != true) throw IOException(t("图片发送失败"))
                    delay(150)
                }
                if (!isCurrentConversation(conversationVersion)) return@launch
                if (pty?.sendLine(actualPrompt) != true) throw IOException(t("聊天尚未连接"))
                rememberRunningRequest(
                    RunningRequest(baselineId, actualPrompt, knownSessionIds),
                )
                startPollingForReply(baselineId, actualPrompt, knownSessionIds, conversationVersion)
            }.onFailure { error ->
                if ((error !is CancellationException || error is TimeoutCancellationException) &&
                    isCurrentConversation(conversationVersion)) {
                    finishRunningRequest(_state.value.currentSessionId)
                    updateConversation(conversationVersion) { current ->
                        current.copy(
                            messages = current.messages.map { message ->
                                if (message === pending) message.copy(pending = false) else message
                            },
                            isGenerating = false,
                            generatingConversationVersion = null,
                            liveActivity = "",
                            error = friendlyError(error),
                        )
                    }
                }
            }
        }
        return true
    }

    fun stopGeneration() {
        val conversationVersion = _state.value.conversationVersion
        finishRunningRequest(_state.value.currentSessionId)
        sendJob?.cancel()
        pty?.stop()
        pollJob?.cancel()
        updateConversation(conversationVersion) {
            it.copy(
                isGenerating = false,
                generatingConversationVersion = null,
                liveActivity = "",
            )
        }
        refreshMessages(conversationVersion)
        refreshSessions()
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val version = _state.value.conversationVersion
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    uris.take((MAX_IMAGES - _state.value.pendingImages.size).coerceAtLeast(0)).map { readImage(it) }
                }
            }.onSuccess { images ->
                updateConversation(version) { it.copy(pendingImages = (it.pendingImages + images).take(MAX_IMAGES), error = null) }
            }.onFailure { showError(friendlyError(it)) }
        }
    }

    fun removeImage(uri: String) {
        _state.update { it.copy(pendingImages = it.pendingImages.filterNot { image -> image.uri == uri }) }
    }

    fun reconnect() {
        startAutoReconnect(forceRestart = true)
    }

    fun editConnection() {
        accountVersion++
        authenticationJob?.cancel()
        modelJob?.cancel()
        resetInteraction()
        rememberCurrentSessionDisplay()
        unboundRunningRequest = null
        cancelReconnect()
        navigationJob?.cancel()
        sendJob?.cancel()
        pollJob?.cancel()
        pty?.close()
        pty = null
        _state.update {
            it.copy(
                destination = Destination.LOGIN,
                savedConfig = client.savedConfig(),
                busy = false,
                error = null,
                connectionError = null,
                modelSettingsOpen = false,
                modelSettingsBusy = false,
                messages = emptyList(),
                sessions = emptyList(),
                currentSessionId = null,
                currentTitle = null,
                draftSession = false,
                connectionState = ConnectionState.IDLE,
                showReconnectPrompt = false,
                ptyReady = false,
                isGenerating = false,
                generatingConversationVersion = null,
                liveActivity = "",
                conversationVersion = it.conversationVersion + 1,
            )
        }
    }

    fun logout() {
        accountVersion++
        authenticationJob?.cancel()
        modelJob?.cancel()
        resetInteraction()
        viewModelScope.launch {
            cancelReconnect()
            navigationJob?.cancel()
            sendJob?.cancel()
            pollJob?.cancel()
            pty?.close()
            pty = null
            client.logout()
            sessionMessages.clear()
            sessionActivities.clear()
            runningRequests.clear()
            attachIds.clear()
            unboundRunningRequest = null
            _state.value = AppUiState(
                destination = Destination.LOGIN,
                conversationVersion = _state.value.conversationVersion + 1,
                savedConfig = client.savedConfig(),
                voiceRepliesEnabled = client.voiceRepliesEnabled(),
            )
        }
    }

    fun refreshSessions() {
        val config = client.savedConfig()
        viewModelScope.launch {
            runCatching { client.getSessions() }
                .onSuccess { sessions ->
                    if (client.savedConfig() != config || _state.value.destination != Destination.CHAT) return@onSuccess
                    _state.update { current ->
                        val persisted = current.currentSessionId?.let { id ->
                            sessions.firstOrNull { it.id == id }
                        }
                        current.copy(
                            sessions = sessions,
                            draftSession = current.draftSession && persisted == null,
                            currentTitle = current.currentTitle
                                ?: persisted?.title
                                ?: persisted?.preview,
                        )
                    }
                }
                // A failed history refresh must not cover cached chat content with a toast.
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun setVoiceRepliesEnabled(enabled: Boolean) {
        client.setVoiceRepliesEnabled(enabled)
        _state.update {
            it.copy(
                voiceRepliesEnabled = enabled,
                speechRequest = if (enabled) it.speechRequest else null,
            )
        }
    }

    fun consumeSpeech(id: Long) {
        _state.update { current ->
            if (current.speechRequest?.id == id) current.copy(speechRequest = null) else current
        }
    }

    override fun onState(state: ConnectionState) {
        if (state != ConnectionState.OPEN) {
            _state.update { it.copy(interactionReady = false) }
        }
        _state.update { current ->
            current.copy(
                connectionState = state,
                connectionError = if (state == ConnectionState.OPEN) null else current.connectionError,
                showReconnectPrompt = if (state == ConnectionState.OPEN) {
                    false
                } else {
                    current.showReconnectPrompt
                },
                ptyReady = current.ptyReady && state == ConnectionState.OPEN,
            )
        }
        when (state) {
            ConnectionState.OPEN -> cancelReconnect()
            ConnectionState.CLOSED -> startAutoReconnect()
            else -> Unit
        }
    }

    override fun onSessionId(sessionId: String) {
        attachIds[sessionId] = conversationAttachId
        _state.update { current -> current.copy(currentSessionId = sessionId,
            promptHistory = current.promptHistory.map { record ->
                if (record.sessionKey == "draft-" + current.conversationVersion) record.copy(sessionKey = sessionId) else record
            }) }
        unboundRunningRequest?.let { request ->
            runningRequests[sessionId] = request
            unboundRunningRequest = null
        }
        val current = _state.value
        if (current.messages.isNotEmpty()) sessionMessages[sessionId] = current.messages
        if (current.liveActivity.isNotBlank()) sessionActivities[sessionId] = current.liveActivity
        refreshSessions()
    }

    override fun onRawOutput(bytes: ByteArray) {
        promptTracker.append(bytes)
        val socket = pty
        val owner = _state.value.conversationVersion
        // Only fixed VT status/cursor replies, never Enter, text answers or consent.
        val replies = promptTracker.takeReplies()
        if (socket != null && replies.isNotEmpty()) viewModelScope.launch {
            for (reply in replies) socket.sendInteractive(reply.toByteArray()) {
                pty === socket && isCurrentConversation(owner)
            }
        }
        _state.update { it.copy(interactionReady = false) }
        if (promptRefresh?.isActive != true) {
            promptRefresh = viewModelScope.launch {
                delay(60)
                refreshPrompt()
            }
        }
    }

    private fun refreshPrompt() {
        promptTracker.refresh()
        _state.update { it.copy(prompt = promptTracker.current,
            interactionKind = promptTracker.current?.kind,
            interactionReady = promptTracker.ready && pty?.isConnected() == true,
            promptBusy = promptTracker.busy,
            speechRequest = if (promptTracker.current != null) null else it.speechRequest) }
    }

    override fun onOutput(text: String) {
        readinessBuffer = (readinessBuffer + text).takeLast(4_000)
        val status = liveStatus.append(text)
        _state.update { current ->
            current.copy(
                liveActivity = status.ifBlank { current.liveActivity },
                ptyReady = current.ptyReady || PtyReadiness.isReady(readinessBuffer),
            )
        }
        _state.value.currentSessionId?.let { sessionId ->
            sessionActivities[sessionId] = _state.value.liveActivity
        }
    }

    fun answerPrompt(id: Long, text: String): Boolean {
        val prompt = _state.value.prompt?.takeIf { it.id == id } ?: return false
        val bytes = HermesPromptInput.text(prompt, text) ?: return false
        return sendPrompt(prompt, bytes, if (prompt.kind == HermesInteractionKind.SECRET) null else text)
    }

    fun choosePrompt(id: Long, key: String): Boolean {
        val prompt = _state.value.prompt?.takeIf { it.id == id } ?: return false
        val bytes = HermesPromptInput.choice(prompt, key) ?: return false
        val label = prompt.choices.first { it.key == key }.label
        val reply = label.takeUnless { prompt.multiSelect || it.startsWith("Other (") || it == "Show full command" }
        return sendPrompt(prompt, bytes, reply)
    }

    fun confirmPrompt(id: Long): Boolean {
        val prompt = _state.value.prompt?.takeIf { it.id == id } ?: return false
        val bytes = HermesPromptInput.confirmMultiple(prompt) ?: return false
        val reply = prompt.choices.filter { it.checked }.joinToString(", ") { it.label }
        return sendPrompt(prompt, bytes, reply.takeUnless { it.contains("Other (") })
    }

    fun skipPromptSecret(id: Long): Boolean {
        val prompt = _state.value.prompt?.takeIf { it.id == id } ?: return false
        return sendPrompt(prompt, HermesPromptInput.skipSecret(prompt) ?: return false, null)
    }

    private fun sendPrompt(prompt: HermesPrompt, bytes: ByteArray, reply: String?): Boolean {
        val socket = pty
        if (socket?.isConnected() != true || !_state.value.interactionReady) { bytes.fill(0); return false }
        val lease = promptTracker.begin(prompt.id) ?: run { bytes.fill(0); return false }
        val owner = _state.value.conversationVersion
        _state.update { it.copy(promptBusy = true, interactionReady = false) }
        promptWriter = viewModelScope.launch {
            try {
                fun allowed() = pty === socket && isCurrentConversation(owner) && promptTracker.valid(lease)
                if (!socket.sendInteractive(bytes, ::allowed)) {
                    promptTracker.failed(lease)
                    refreshPrompt()
                    showError(t("回应未发送，请确认当前问题后重试"))
                } else {
                    promptTracker.sent(lease)
                    if (reply != null && prompt.kind != HermesInteractionKind.SECRET) {
                        val current = _state.value
                        val key = current.currentSessionId ?: "draft-" + current.conversationVersion
                        val record = PromptExchange(prompt.id, key, ChatPresentation.visible(current.messages).size,
                            prompt.body, reply)
                        _state.update { it.copy(promptHistory = (it.promptHistory + record).takeLast(100)) }
                    }
                    followInteractionReply(socket, owner)
                    // Successful transport is not a server acknowledgement. Keep the old request
                    // disabled until the terminal actually redraws or removes it; never auto-resend.
                }
            } finally { bytes.fill(0) }
        }
        return true
    }

    private fun resetInteraction() {
        promptRefresh?.cancel()
        promptWriter?.cancel()
        interactionRefresh?.cancel()
        promptTracker.clear()
        _state.update { it.copy(prompt = null, promptBusy = false, interactionKind = null,
            interactionReady = false) }
    }

    private fun followInteractionReply(socket: HermesPtySocket, owner: Long) {
        if (pollJob?.isActive == true) return
        interactionRefresh?.cancel()
        val baseline = _state.value.messages.mapNotNull { it.id }.maxOrNull() ?: 0
        interactionRefresh = viewModelScope.launch {
            repeat(300) {
                delay(1000)
                if (pty !== socket || !isCurrentConversation(owner)) return@launch
                val id = _state.value.currentSessionId ?: return@repeat
                val messages = runCatching { client.getMessages(id) }.getOrNull() ?: return@repeat
                if (pty !== socket || !isCurrentConversation(owner)) return@launch
                sessionMessages[id] = messages
                updateConversation(owner) { it.copy(messages = ChatPresentation.retainUntilReplacement(it.messages, messages)) }
                if (messages.any { (it.id ?: 0) > baseline && it.role == "assistant" &&
                        it.toolCalls.isNullOrEmpty() && it.content.isNotBlank() }) {
                    updateConversation(owner) { it.copy(interactionKind = null) }
                    refreshSessions()
                    return@launch
                }
            }
        }
    }

    override fun onError(message: String) {
        _state.update { it.copy(connectionError = message) }
    }

    override fun onCleared() {
        authenticationJob?.cancel()
        cancelReconnect()
        navigationJob?.cancel()
        sendJob?.cancel()
        pollJob?.cancel()
        modelJob?.cancel()
        resetInteraction()
        pty?.close()
        super.onCleared()
    }

    private fun restoreSession() {
        val owner = accountVersion
        authenticationJob = viewModelScope.launch {
            val config = client.savedConfig()
            if (config == null || !client.hasSavedSession()) {
                _state.update { it.copy(destination = Destination.LOGIN, savedConfig = config) }
                return@launch
            }
            _state.update { it.copy(destination = Destination.CHAT, busy = false) }
            runCatching {
                withTimeout(30_000) {
                    client.checkAuthenticated()
                    loadHome(owner)
                }
            }.onFailure { error ->
                if (owner != accountVersion) return@onFailure
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                // Keep saved settings, but let the user fix failed login instead of retrying invisibly.
                _state.update { it.copy(destination = Destination.LOGIN, savedConfig = config,
                    busy = false, error = loginFailureMessage(error), connectionState = ConnectionState.CLOSED) }
            }
        }
    }

    private suspend fun loadHome(owner: Long) {
        val sessions = client.getSessions()
        conversationAttachId = attachIdForConnection(true)
        val model = withTimeoutOrNull(5_000) { runCatching { client.getModelInfo() }.getOrNull() }
        currentCoroutineContext().ensureActive()
        if (owner != accountVersion) return
        _state.update {
            it.copy(
                destination = Destination.CHAT,
                savedConfig = client.savedConfig(),
                sessions = sessions,
                modelInfo = model,
                currentSessionId = null,
                currentTitle = null,
                draftSession = false,
                showReconnectPrompt = false,
                busy = false,
                error = null,
            )
        }
    }

    private fun startAutoReconnect(forceRestart: Boolean = false) {
        val snapshot = _state.value
        if (snapshot.destination != Destination.CHAT || snapshot.connectionState == ConnectionState.ENDED) {
            return
        }
        if (forceRestart) {
            cancelReconnect()
            _state.update { it.copy(showReconnectPrompt = false) }
        }
        val conversationVersion = snapshot.conversationVersion
        if (reconnectPromptJob?.isActive != true) {
            reconnectPromptJob = viewModelScope.launch {
                delay(RECONNECT_PROMPT_DELAY_MS)
                updateConversation(conversationVersion) { current ->
                    current.copy(
                        showReconnectPrompt = ReconnectPromptPolicy.shouldShow(
                            current.connectionState,
                            RECONNECT_PROMPT_DELAY_MS,
                        ),
                    )
                }
            }
        }
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            var attempt = 0
            while (isCurrentConversation(conversationVersion)) {
                val current = _state.value
                if (current.destination != Destination.CHAT ||
                    current.connectionState == ConnectionState.OPEN ||
                    current.connectionState == ConnectionState.ENDED
                ) {
                    return@launch
                }
                runCatching {
                    withTimeout(RECONNECT_ATTEMPT_TIMEOUT_MS) {
                    connectSocket(
                        sessionId = current.currentSessionId,
                        fresh = current.currentSessionId == null,
                        conversationVersion = conversationVersion,
                    )
                        state.first {
                            it.conversationVersion != conversationVersion ||
                                it.connectionState == ConnectionState.OPEN ||
                                it.connectionState == ConnectionState.CLOSED ||
                                it.connectionState == ConnectionState.ENDED
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    updateConversation(conversationVersion) { it.copy(connectionState = ConnectionState.CLOSED,
                        connectionError = friendlyError(error)) }
                }
                if (!isCurrentConversation(conversationVersion) ||
                    _state.value.connectionState == ConnectionState.OPEN
                ) {
                    return@launch
                }
                delay(ReconnectPromptPolicy.retryDelay(attempt++))
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectPromptJob?.cancel()
        reconnectJob = null
        reconnectPromptJob = null
    }

    private suspend fun ensureSocket(conversationVersion: Long) {
        if (!isCurrentConversation(conversationVersion)) throw CancellationException()
        if (pty?.isConnected() != true) {
            if (_state.value.connectionState != ConnectionState.CONNECTING) connectSocket(
                _state.value.currentSessionId,
                fresh = _state.value.currentSessionId == null,
                conversationVersion = conversationVersion,
            )
            withTimeout(20_000) {
                state.first {
                    it.conversationVersion != conversationVersion ||
                        it.connectionState == ConnectionState.OPEN
                }
            }
        }
        if (!isCurrentConversation(conversationVersion)) throw CancellationException()
        withTimeout(30_000) {
            state.first {
                it.conversationVersion != conversationVersion ||
                    (it.connectionState == ConnectionState.OPEN && it.ptyReady)
            }
        }
        if (!isCurrentConversation(conversationVersion)) throw CancellationException()
    }

    private suspend fun connectSocket(
        sessionId: String?,
        fresh: Boolean,
        conversationVersion: Long,
    ) {
        if (!isCurrentConversation(conversationVersion)) return
        resetInteraction()
        pty?.close()
        readinessBuffer = ""
        liveStatus.reset(_state.value.liveActivity)
        updateConversation(conversationVersion) { it.copy(ptyReady = false) }
        var candidate: HermesPtySocket? = null
        val scopedListener = object : HermesPtySocket.Listener {
            private fun isCurrent(): Boolean =
                pty === candidate && isCurrentConversation(conversationVersion) && _state.value.destination == Destination.CHAT

            override fun onState(state: ConnectionState) {
                viewModelScope.launch { if (isCurrent()) this@AppViewModel.onState(state) }
            }

            override fun onSessionId(sessionId: String) {
                viewModelScope.launch { if (isCurrent()) this@AppViewModel.onSessionId(sessionId) }
            }

            override fun onRawOutput(bytes: ByteArray) {
                viewModelScope.launch { if (isCurrent()) this@AppViewModel.onRawOutput(bytes) }
            }

            override fun onOutput(text: String) {
                viewModelScope.launch { if (isCurrent()) this@AppViewModel.onOutput(text) }
            }

            override fun onError(message: String) {
                viewModelScope.launch { if (isCurrent()) this@AppViewModel.onError(message) }
            }
        }
        candidate = HermesPtySocket(client, scopedListener)
        pty = candidate
        candidate.connect(sessionId, fresh, conversationAttachId)
    }

    private fun startPollingForReply(
        baselineId: Long,
        expectedPrompt: String,
        knownSessionIds: Set<String>,
        conversationVersion: Long,
    ) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val normalizedPrompt = expectedPrompt.replace(Regex("\\s+"), " ").trim()
            val expectedPrefix = normalizedPrompt.take(80)
            var promptPersisted = false
            var attempt = 0
            while (attempt < 300 || _state.value.interactionKind != null) {
                delay(1_000)
                if (!isCurrentConversation(conversationVersion)) return@launch

                var sessionId = _state.value.currentSessionId
                var messages = sessionId?.let { resolvedSessionId ->
                    runCatching { client.getMessages(resolvedSessionId) }.getOrNull()
                }
                if (messages != null) {
                    sessionId?.let { sessionMessages[it] = messages }
                    promptPersisted = promptPersisted ||
                        ChatPresentation.prompt(messages, baselineId, expectedPrompt) != null
                }

                if (!promptPersisted &&
                    SessionDiscoveryPolicy.shouldDiscover(sessionId, knownSessionIds)
                ) {
                    val sessions = runCatching { client.getSessions() }.getOrNull()
                    val unseen = sessions.orEmpty().filterNot { it.id in knownSessionIds }
                    val candidate = unseen.firstOrNull { session ->
                        session.preview.orEmpty()
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .contains(expectedPrefix)
                    }
                    if (candidate != null) {
                        sessionId = candidate.id
                        runningRequests[candidate.id] = RunningRequest(
                            baselineId,
                            expectedPrompt,
                            knownSessionIds,
                        )
                        unboundRunningRequest = null
                        messages = runCatching { client.getMessages(candidate.id) }.getOrNull()
                        if (messages != null) {
                            sessionMessages[candidate.id] = messages
                            promptPersisted = promptPersisted ||
                                ChatPresentation.prompt(messages, baselineId, expectedPrompt) != null
                        }
                    }
                    if (sessions != null) {
                        updateConversation(conversationVersion) { current ->
                            current.copy(
                                sessions = sessions,
                                currentSessionId = candidate?.id ?: current.currentSessionId,
                                currentTitle = candidate?.title
                                    ?: candidate?.preview
                                    ?: current.currentTitle,
                            )
                        }
                    }
                }

                if (!isCurrentConversation(conversationVersion)) return@launch
                if (messages != null && promptPersisted) {
                    updateConversation(conversationVersion) { it.copy(messages = messages) }
                }

                if (_state.value.interactionKind == null) attempt++
                when (if (_state.value.interactionKind != null) PromptDeliveryAction.NONE else
                    PromptDeliveryPolicy.action(attempt - 1, promptPersisted)) {
                    PromptDeliveryAction.NONE -> Unit
                    PromptDeliveryAction.FAIL -> {
                        finishRunningRequest(_state.value.currentSessionId)
                        updateConversation(conversationVersion) { current ->
                            current.copy(
                                isGenerating = false,
                                generatingConversationVersion = null,
                                liveActivity = "",
                                error = t("尚未确认消息是否送达，请刷新会话后再决定是否重试（未自动重发）"),
                            )
                        }
                        return@launch
                    }
                }
                if (!promptPersisted) continue

                val userMessage = ChatPresentation.prompt(messages.orEmpty(), baselineId, expectedPrompt)
                val completedMessage = userMessage?.id?.let { ChatPresentation.answer(messages.orEmpty(), it) }
                if (completedMessage != null) {
                    finishRunningRequest(sessionId)
                    delay(350)
                    updateConversation(conversationVersion) { current ->
                        current.copy(
                            isGenerating = false,
                            generatingConversationVersion = null,
                            liveActivity = "",
                            speechRequest = if (current.voiceRepliesEnabled) {
                                SpeechRequest(
                                    id = nextSpeechRequestId++,
                                    text = SpeechText.concise(completedMessage.content),
                                )
                            } else {
                                null
                            },
                        )
                    }
                    refreshSessions()
                    return@launch
                }
            }
            finishRunningRequest(_state.value.currentSessionId)
            updateConversation(conversationVersion) {
                it.copy(
                    isGenerating = false,
                    generatingConversationVersion = null,
                    error = t("等待回答超时，可下拉刷新后重试"),
                )
            }
        }
    }

    private fun refreshMessages(conversationVersion: Long = _state.value.conversationVersion) {
        val sessionId = _state.value.currentSessionId ?: return
        viewModelScope.launch {
            runCatching { client.getMessages(sessionId) }
                .onSuccess { messages ->
                    if (!isCurrentConversation(conversationVersion)) return@onSuccess
                    if (messages.isNotEmpty()) sessionMessages[sessionId] = messages
                    updateConversation(conversationVersion) {
                        it.copy(messages = ChatPresentation.retainUntilReplacement(it.messages, messages))
                    }
                }
        }
    }

    private fun rememberCurrentSessionDisplay() {
        val current = _state.value
        val sessionId = current.currentSessionId ?: return
        if (current.messages.isNotEmpty()) sessionMessages[sessionId] = current.messages
        if (current.liveActivity.isNotBlank()) sessionActivities[sessionId] = current.liveActivity
    }

    private fun rememberRunningRequest(request: RunningRequest) {
        val sessionId = _state.value.currentSessionId
        if (sessionId == null) {
            unboundRunningRequest = request
        } else {
            runningRequests[sessionId] = request
            unboundRunningRequest = null
        }
    }

    private fun finishRunningRequest(sessionId: String?) {
        resetInteraction()
        sessionId?.let(runningRequests::remove)
        unboundRunningRequest = null
    }

    private fun isCurrentConversation(conversationVersion: Long): Boolean =
        _state.value.conversationVersion == conversationVersion

    private inline fun updateConversation(
        conversationVersion: Long,
        transform: (AppUiState) -> AppUiState,
    ) {
        _state.update { current ->
            ConversationState.updateIfCurrent(current, conversationVersion, transform)
        }
    }

    private fun readImage(uri: Uri): PickedImage {
        val resolver = getApplication<Application>().contentResolver
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: throw IllegalArgumentException(t("只支持图片文件"))
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "image-${System.currentTimeMillis()}.${mime.substringAfter('/')}"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMAGE_BYTES) {
                    throw IllegalArgumentException(t("单张图片不能超过 20 MB"))
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IOException(t("无法读取图片"))
        return PickedImage(uri.toString(), name, mime, bytes)
    }

    fun onBackground() { backgroundSince = android.os.SystemClock.elapsedRealtime() }

    fun onForeground() {
        val backgroundDuration = backgroundSince?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0
        backgroundSince = null
        if (_state.value.destination != Destination.CHAT) return
        refreshMessages()
        refreshSessions()
        if (pty != null && _state.value.connectionState != ConnectionState.ENDED &&
            (backgroundDuration >= 15_000 || _state.value.connectionState == ConnectionState.CLOSED) &&
            sendJob?.isActive != true) {
            _state.update { it.copy(connectionState = ConnectionState.CLOSED, ptyReady = false) }
            startAutoReconnect(forceRestart = true)
        }
    }

    fun openModelSettings() {
        if (_state.value.modelSettingsBusy) return
        _state.update { it.copy(modelSettingsOpen = true, modelSettingsBusy = true,
            modelConfirmation = false, modelSettingsError = null) }
        modelJob = viewModelScope.launch {
            runCatching {
                val options = client.getModelOptions()
                val reasoning = client.getReasoning()
                _state.update { it.copy(modelOptions = options, reasoning = reasoning, modelSettingsBusy = false) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(modelSettingsBusy = false, modelSettingsError = friendlyError(error)) }
            }
        }
    }

    fun closeModelSettings() {
        if (_state.value.modelSettingsBusy) return
        _state.update { it.copy(modelSettingsOpen = false, modelConfirmation = false) }
    }

    fun applyModelSettings(provider: String, model: String, reasoning: String, confirmed: Boolean) {
        if (_state.value.modelSettingsBusy || runningRequests.isNotEmpty() || unboundRunningRequest != null) {
            _state.update { it.copy(modelSettingsError = t("请等待运行中的回答结束后再切换模型")) }
            return
        }
        _state.update { it.copy(modelSettingsBusy = true, modelSettingsError = null) }
        modelJob = viewModelScope.launch {
            runCatching {
                if (!client.setModel(provider, model, confirmed)) {
                    _state.update { it.copy(modelConfirmation = true, modelSettingsBusy = false) }
                    return@launch
                }
                client.setReasoning(reasoning)
                val info = client.getModelInfo()
                _state.update { it.copy(modelInfo = info, reasoning = reasoning,
                    modelSettingsBusy = false, modelSettingsOpen = false, modelConfirmation = false) }
                // Hermes applies profile settings when starting a new terminal conversation.
                newChat()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(modelSettingsBusy = false, modelSettingsError =
                    t("设置未全部完成，请重新读取确认。\n") + friendlyError(error)) }
            }
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    private fun loginFailureMessage(error: Throwable): String =
        t("无法登录，请检查 fnOS 是否已开启，并确认服务器和账号信息。具体原因：") + friendlyError(error)

    private fun friendlyError(error: Throwable): String = when (error) {
        is TimeoutCancellationException -> t("Hermes 启动超时，请稍后重试")
        is java.net.SocketTimeoutException -> t("连接超时，请检查服务器地址、端口和 HTTP/HTTPS 协议。")
        is java.net.UnknownHostException -> t("找不到服务器，请检查地址")
        is javax.net.ssl.SSLException -> t("服务器证书校验失败，请检查 HTTPS 证书链")
        is java.net.ConnectException -> t("无法连接服务器，请检查地址和端口")
        else -> error.message ?: t("操作失败，请重试")
    }

    companion object {
        private const val RECONNECT_PROMPT_DELAY_MS = 10_000L
        private const val RECONNECT_ATTEMPT_TIMEOUT_MS = 6_000L
        private const val MAX_IMAGES = 5
        private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    }
}

class AppViewModelFactory(
    private val application: Application,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(application, container.client, container::attachId) as T
    }
}
