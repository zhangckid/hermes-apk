package win.catgo.gpt.ui

import win.catgo.gpt.i18n.t
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import win.catgo.gpt.R
import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.PickedImage
import win.catgo.gpt.model.ServerConfig
import win.catgo.gpt.model.SessionSummary
import win.catgo.gpt.network.ConnectionState
import win.catgo.gpt.ui.theme.Clay
import win.catgo.gpt.ui.theme.ElectricBlue
import win.catgo.gpt.ui.theme.Hairline
import win.catgo.gpt.ui.theme.InkBlack
import win.catgo.gpt.ui.theme.MutedText
import win.catgo.gpt.ui.theme.PaperWhite
import win.catgo.gpt.ui.theme.RaisedSurface
import win.catgo.gpt.ui.theme.WarmSurface

@Composable
fun CatgoApp(state: AppUiState, viewModel: AppViewModel) {
    CatgoHome(state, viewModel)
}

@Composable
private fun CatgoHome(state: AppUiState, viewModel: AppViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(t(it))
            viewModel.clearError()
        }
    }

    key(state.conversationVersion, state.destination) { VoiceReplyEffect(
        enabled = state.voiceRepliesEnabled && state.interactionKind == null,
        request = state.speechRequest,
        onConsumed = viewModel::consumeSpeech,
    ) }

    SecurePromptEffect(state.prompt?.kind == win.catgo.gpt.network.HermesInteractionKind.SECRET)
    if (state.modelSettingsOpen) ModelSettingsDialog(state, viewModel::closeModelSettings,
        viewModel::applyModelSettings, viewModel::openModelSettings)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.destination) {
            Destination.LOADING -> LoadingScreen()
            Destination.LOGIN -> LoginScreen(
                savedConfig = state.savedConfig,
                busy = state.busy,
                snackbarHostState = snackbarHostState,
                onLogin = viewModel::login,
            )
            Destination.CHAT -> ChatScreen(
                state = state,
                snackbarHostState = snackbarHostState,
                onModelSettings = viewModel::openModelSettings,
                onPromptChoice = viewModel::choosePrompt,
                onPromptConfirm = viewModel::confirmPrompt,
                onPromptAnswer = viewModel::answerPrompt,
                onPromptSkip = viewModel::skipPromptSecret,
                onNewChat = viewModel::newChat,
                onOpenSession = viewModel::openSession,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration,
                onAddImages = viewModel::addImages,
                onRemoveImage = viewModel::removeImage,
                onReconnect = viewModel::reconnect,
                onEditConnection = viewModel::editConnection,
                onLogout = viewModel::logout,
                onRefreshSessions = viewModel::refreshSessions,
                onVoiceRepliesChanged = viewModel::setVoiceRepliesEnabled,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ElectricBlue)
    }
}

@Composable
internal fun LoginScreen(
    savedConfig: ServerConfig?,
    busy: Boolean,
    snackbarHostState: SnackbarHostState,
    onLogin: (ServerConfig, String) -> Unit,
) {
    var host by rememberSaveable(savedConfig) { mutableStateOf(savedConfig?.host ?: "") }
    var port by rememberSaveable(savedConfig) { mutableStateOf(savedConfig?.port?.toString() ?: "443") }
    var username by rememberSaveable(savedConfig) { mutableStateOf(savedConfig?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        onLogin(
            ServerConfig(
                host = host,
                port = port.toIntOrNull() ?: 0,
                username = username,
            ),
            password,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = InkBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Image(
                    painter = painterResource(R.drawable.catgo_icon),
                    contentDescription = "catgo-gpt",
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(30.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(22.dp))
                Text("catgo-gpt", style = MaterialTheme.typography.titleLarge, color = PaperWhite)
                Spacer(Modifier.height(6.dp))
                Text(t("连接你的 Hermes Agent"), color = MutedText)
                Spacer(Modifier.height(28.dp))
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    colors = CardDefaults.cardColors(containerColor = WarmSurface),
                    border = BorderStroke(1.dp, Hairline),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(t("服务器"), color = MutedText)
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            modifier = Modifier.fillMaxWidth().testTag("login-server"),
                            placeholder = { Text(ServerConfig.EXAMPLE_HOST, color = MutedText) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                            ),
                            supportingText = { Text(t("使用 HTTPS / WSS 安全连接")) },
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { value -> port = value.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("端口")) },
                            placeholder = { Text("443") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("用户名")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(t("密码")) },
                            singleLine = true,
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) t("隐藏密码") else t("显示密码"),
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                        )
                        Button(
                            onClick = { submit() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(t("连接并登录"))
                            }
                        }
                        Text(
                            t("服务器配置会保留；密码和登录凭据由 Android Keystore 加密，用于自动重联。"),
                            color = MutedText,
                            fontSize = 12.sp,
                        )
                        HorizontalDivider()
                        LanguagePicker()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    state: AppUiState,
    snackbarHostState: SnackbarHostState,
    onNewChat: () -> Unit,
    onModelSettings: () -> Unit,
    onPromptChoice: (Long, String) -> Boolean,
    onPromptConfirm: (Long) -> Boolean,
    onPromptAnswer: (Long, String) -> Boolean,
    onPromptSkip: (Long) -> Boolean,
    onOpenSession: (SessionSummary) -> Unit,
    onSend: (String) -> Boolean,
    onStop: () -> Unit,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onReconnect: () -> Unit,
    onEditConnection: () -> Unit,
    onLogout: () -> Unit,
    onRefreshSessions: () -> Unit,
    onVoiceRepliesChanged: (Boolean) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isGenerating = ConversationState.isGenerating(state)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HistoryDrawer(
                sessions = state.sessions,
                currentSessionId = state.currentSessionId,
                draftSession = state.draftSession,
                model = state.modelInfo?.model,
                voiceRepliesEnabled = state.voiceRepliesEnabled,
                onNewChat = {
                    onNewChat()
                    scope.launch { drawerState.close() }
                },
                onOpenSession = {
                    onOpenSession(it)
                    scope.launch { drawerState.close() }
                },
                onRefresh = onRefreshSessions,
                onVoiceRepliesChanged = onVoiceRepliesChanged,
                onEditConnection = onEditConnection,
                onLogout = onLogout,
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = InkBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = InkBlack,
                        titleContentColor = PaperWhite,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = t("会话历史"))
                        }
                    },
                    title = {
                        Text(
                            state.currentTitle?.takeIf { it.isNotBlank() } ?: t("新对话"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        )
                    },
                    actions = {
                        TextButton(onClick = onModelSettings, enabled = !isGenerating && state.prompt == null) {
                            Text(state.modelInfo?.model?.substringAfterLast('/')?.take(18) ?: t("模型"),
                                maxLines = 1, fontSize = 11.sp)
                        }
                        IconButton(onClick = onNewChat) {
                            Icon(Icons.Default.Add, contentDescription = t("新对话"))
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .imePadding().navigationBarsPadding()) {
                key(state.currentSessionId ?: "draft-${state.conversationVersion}") {
                    Conversation(
                        state = state,
                        isGenerating = isGenerating,
                        modifier = Modifier.weight(1f),
                        onReconnect = onReconnect,
                        onPromptChoice = onPromptChoice, onPromptConfirm = onPromptConfirm,
                        onPromptAnswer = onPromptAnswer, onPromptSkip = onPromptSkip,
                    )
                }
                key(state.conversationVersion) {
                    Composer(
                        conversationVersion = state.conversationVersion,
                        pendingImages = state.pendingImages,
                        isGenerating = if (state.prompt != null) !(state.prompt.canAnswerText && state.connectionState == ConnectionState.OPEN && !state.promptBusy) else isGenerating,
                        allowImages = state.prompt == null,
                        onAddImages = onAddImages,
                        onRemoveImage = onRemoveImage,
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDrawer(
    sessions: List<SessionSummary>,
    currentSessionId: String?,
    draftSession: Boolean,
    model: String?,
    voiceRepliesEnabled: Boolean,
    onNewChat: () -> Unit,
    onOpenSession: (SessionSummary) -> Unit,
    onRefresh: () -> Unit,
    onVoiceRepliesChanged: (Boolean) -> Unit,
    onEditConnection: () -> Unit,
    onLogout: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        if (query.isBlank()) sessions else sessions.filter {
            (it.title.orEmpty() + it.preview.orEmpty()).contains(query, ignoreCase = true)
        }
    }
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(320.dp),
        drawerContainerColor = WarmSurface,
        drawerContentColor = PaperWhite,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(R.drawable.catgo_icon),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text("catgo-gpt", style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = t("刷新"))
                }
            }
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RaisedSurface),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(t("新对话"))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(t("搜索对话")) },
                singleLine = true,
            )
            Text(t("最近"), modifier = Modifier.padding(horizontal = 20.dp), color = MutedText, fontSize = 12.sp)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (draftSession &&
                    (query.isBlank() || t("新对话").contains(query, ignoreCase = true))
                ) {
                    item(key = "local-new-chat") {
                        SessionRow(
                            session = SessionSummary(id = "local-new-chat", title = t("新对话")),
                            selected = true,
                            onClick = onNewChat,
                        )
                    }
                }
                items(filtered, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        selected = session.id == currentSessionId,
                        onClick = { onOpenSession(session) },
                    )
                }
            }
            HorizontalDivider(color = Hairline)
            if (!model.isNullOrBlank()) {
                Text(
                    model.substringAfterLast('/'),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    color = MutedText,
                    fontSize = 12.sp,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVoiceRepliesChanged(!voiceRepliesEnabled) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MutedText)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(t("语音回答"), fontSize = 14.sp)
                    Text(t("朗读精简要点，文字保留完整回答"), color = MutedText, fontSize = 11.sp)
                }
                Switch(
                    checked = voiceRepliesEnabled,
                    onCheckedChange = onVoiceRepliesChanged,
                )
            }
            TextButton(onClick = onEditConnection, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text(t("服务器设置"))
            }
            LanguagePicker()
            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                colors = ButtonDefaults.textButtonColors(contentColor = Clay),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text(t("退出登录"))
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, selected: Boolean, onClick: () -> Unit) {
    val timestamp = session.lastActive ?: session.lastActivityAt
    val dateText = timestamp?.let {
        SimpleDateFormat(t("M月d日"), win.catgo.gpt.i18n.UiText.locale(androidx.compose.ui.platform.LocalContext.current)).format(Date((it * 1000).toLong()))
    }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ElectricBlue.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.History, null, modifier = Modifier.size(17.dp), tint = MutedText)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title?.takeIf { it.isNotBlank() } ?: session.preview ?: t("未命名对话"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                color = if (selected) PaperWhite else MaterialTheme.colorScheme.onSurface,
            )
            if (dateText.isNotBlank()) Text(dateText, color = MutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Conversation(
    state: AppUiState,
    isGenerating: Boolean,
    modifier: Modifier,
    onReconnect: () -> Unit,
    onPromptChoice: (Long, String) -> Boolean,
    onPromptConfirm: (Long) -> Boolean,
    onPromptAnswer: (Long, String) -> Boolean,
    onPromptSkip: (Long) -> Boolean,
) {
    val sessionKey = state.currentSessionId ?: "draft-" + state.conversationVersion
    val history = state.promptHistory.filter { it.sessionKey == sessionKey }
    val messages = remember(state.messages) { ChatPresentation.visible(state.messages) }
    val listState = rememberLazyListState()
    var previousCount by remember { mutableStateOf(0) }
    val itemCount = messages.size + history.count { it.afterMessages == 0 || it.afterMessages > messages.size } +
        if (state.prompt != null || isGenerating) 1 else 0
    LaunchedEffect(itemCount, state.prompt?.id, history.size) {
        val nearBottom = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= previousCount - 2
        val outgoing = messages.lastOrNull()?.pending == true
        if (itemCount > 0 && (nearBottom || outgoing || previousCount == 0)) {
            listState.animateScrollToItem(itemCount - 1)
        }
        previousCount = itemCount
    }
    Column(modifier.fillMaxSize()) {
        if (state.showReconnectPrompt) {
            Row(
                Modifier.fillMaxWidth().background(Clay.copy(alpha = 0.12f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("聊天连接已断开"), modifier = Modifier.weight(1f), color = Clay, fontSize = 13.sp)
                TextButton(onClick = onReconnect) { Text(t("重新连接")) }
            }
        }
        if (messages.isEmpty() && !state.busy && !isGenerating && state.prompt == null && history.isEmpty()) {
            EmptyConversation(Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(history.filter { it.afterMessages == 0 }, key = { "prompt-history-" + it.id }) { PromptExchangeItem(it) }
                itemsIndexed(messages, key = { _, message -> message.stableKey }) { index, message ->
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        MessageItem(message)
                        history.filter { it.afterMessages == index + 1 }.forEach { PromptExchangeItem(it) }
                    }
                }
                items(history.filter { it.afterMessages > messages.size }, key = { "prompt-history-" + it.id }) { PromptExchangeItem(it) }
                state.prompt?.let { prompt ->
                    item("prompt-current") {
                        HermesPromptCard(prompt, state.interactionReady, state.promptBusy,
                            onPromptChoice, onPromptConfirm, onPromptAnswer, onPromptSkip)
                    }
                }
                if (isGenerating && state.prompt == null) {
                    item("generating") { ActivityIndicator(state.liveActivity) }
                }
            }
        }
        if (state.busy && messages.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(R.drawable.catgo_icon),
            contentDescription = null,
            modifier = Modifier.size(78.dp).clip(CircleShape).alpha(0.94f),
        )
        Spacer(Modifier.height(20.dp))
        Text(t("今天想一起做什么？"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(t("可以输入文字、说话，或添加图片。"), color = MutedText)
    }
}

@Composable
private fun MessageItem(message: HermesMessage) {
    when (message.role) {
        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                modifier = Modifier.widthIn(max = 540.dp),
                color = RaisedSurface,
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                border = BorderStroke(1.dp, Hairline),
            ) {
                SelectionContainer {
                    Text(
                        message.content,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = PaperWhite.copy(alpha = if (message.pending) 0.72f else 1f),
                    )
                }
            }
        }
        "assistant" -> if (message.content.isNotBlank()) MarkdownContent(message.content)
    }
}

@Composable
private fun ActivityIndicator(liveActivity: String) {
    val status = remember(liveActivity) { HermesLiveStatus.from(liveActivity) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Clay)
        Spacer(Modifier.width(10.dp))
        Text(
            status,
            color = MutedText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MarkdownContent(content: String) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val parts = remember(content) { content.split("```") }
            parts.forEachIndexed { index, part ->
                if (part.isBlank()) return@forEachIndexed
                if (index % 2 == 1) {
                    val firstBreak = part.indexOf('\n')
                    val language = if (firstBreak >= 0) part.substring(0, firstBreak).trim() else ""
                    val code = if (firstBreak >= 0) part.substring(firstBreak + 1).trimEnd() else part
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(WarmSurface, RoundedCornerShape(14.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(14.dp)),
                    ) {
                        if (language.isNotBlank()) {
                            Text(
                                language,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = MutedText,
                                fontSize = 11.sp,
                            )
                            HorizontalDivider(color = Hairline)
                        }
                        Text(
                            code,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(14.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = PaperWhite,
                        )
                    }
                } else {
                    Text(
                        simpleMarkdown(part.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = PaperWhite,
                    )
                }
            }
        }
    }
}

private fun simpleMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < source.length) {
        when {
            source.startsWith("**", index) -> {
                val end = source.indexOf("**", index + 2)
                if (end > index) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(source.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(source[index++])
                }
            }
            source[index] == '`' -> {
                val end = source.indexOf('`', index + 1)
                if (end > index) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = RaisedSurface))
                    append(source.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(source[index++])
                }
            }
            source[index] == '#' && (index == 0 || source[index - 1] == '\n') -> {
                val markerEnd = source.indexOf(' ', index).takeIf { it >= 0 } ?: index
                if (markerEnd > index && markerEnd - index <= 6) {
                    index = markerEnd + 1
                    val lineEnd = source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp))
                    append(source.substring(index, lineEnd))
                    pop()
                    index = lineEnd
                } else {
                    append(source[index++])
                }
            }
            else -> append(source[index++])
        }
    }
}

@Composable
private fun Composer(
    conversationVersion: Long,
    pendingImages: List<PickedImage>,
    isGenerating: Boolean,
    allowImages: Boolean = true,
    onAddImages: (List<android.net.Uri>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: (String) -> Boolean,
    onStop: () -> Unit,
) {
    var input by rememberSaveable(conversationVersion) { mutableStateOf("") }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
        onResult = onAddImages,
    )
    Surface(
        color = InkBlack,
        tonalElevation = 0.dp,
        modifier = Modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (pendingImages.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pendingImages.forEach { image ->
                        Box {
                            AsyncImage(
                                model = image.uri,
                                contentDescription = image.displayName,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            IconButton(
                                onClick = { onRemoveImage(image.uri) },
                                modifier = Modifier.align(Alignment.TopEnd).size(26.dp).background(InkBlack.copy(0.8f), CircleShape),
                            ) {
                                Icon(Icons.Default.Close, t("移除图片"), modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = WarmSurface,
                border = BorderStroke(1.dp, Hairline),
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().then(Modifier.testTag("chat-composer")),
                        placeholder = { Text(t("发消息给 Hermes…"), color = MutedText) },
                        minLines = 1,
                        maxLines = 4,
                        enabled = !isGenerating,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                photoPicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            enabled = allowImages && !isGenerating && pendingImages.size < 5,
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = t("添加图片"))
                        }
                        VoiceInputButton(
                            enabled = !isGenerating,
                            onRecognized = { spoken ->
                                if (!isGenerating && spoken.isNotBlank()) {
                                    val message = listOf(input.trim(), spoken.trim())
                                        .filter(String::isNotBlank).joinToString(" ")
                                    if (onSend(message)) input = "" else input = message
                                }
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(
                            onClick = {
                                if (isGenerating) {
                                    onStop()
                                } else {
                                    if (onSend(input)) input = ""
                                }
                            },
                            enabled = isGenerating || input.isNotBlank() || pendingImages.isNotEmpty(),
                        ) {
                            Icon(
                                if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (isGenerating) t("停止") else t("发送"),
                            )
                        }
                    }
                }
            }
        }
    }
}
