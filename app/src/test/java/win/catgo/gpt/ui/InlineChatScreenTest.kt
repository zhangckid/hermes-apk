package win.catgo.gpt.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.network.ConnectionState
import win.catgo.gpt.network.PromptFixtures

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h892dp")
class InlineChatScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(AppUiState(destination = Destination.CHAT, conversationVersion = 1,
        currentSessionId = "local-test", isGenerating = true, generatingConversationVersion = 1,
        connectionState = ConnectionState.OPEN, interactionReady = true,
        messages = listOf(HermesMessage(id = 1, role = "user", content = "请帮我部署")),
        prompt = PromptFixtures.parse(PromptFixtures.openQuestion()).copy(id = 42)))
    private val sent = mutableListOf<String>()
    private var newChats = 0
    private var settingsOpened = 0
    private fun show() {
        compose.setContent { MaterialTheme {
            ChatScreen(state.value, SnackbarHostState(), onNewChat = { newChats++ }, onModelSettings = {},
                onPromptChoice = { _, _ -> true }, onPromptConfirm = { true },
                onPromptAnswer = { _, _ -> true }, onPromptSkip = { true }, onOpenSession = {},
                onSend = { if (state.value.interactionReady) sent.add(it) else false }, onStop = {},
                onAddImages = {}, onRemoveImage = {}, onReconnect = {}, onEditConnection = { settingsOpened++ },
                onLogout = {}, onRefreshSessions = {}, onVoiceRepliesChanged = {})
        } }
    }

    @Test fun serverSettingsButtonWorksDuringAnActiveConversation() {
        show()
        compose.onNodeWithContentDescription(win.catgo.gpt.i18n.t("会话历史")).performClick()
        compose.onNodeWithTag("server-settings").assertIsDisplayed().performClick()
        assertEquals(1, settingsOpened)
    }

    @Test fun generatingTaskCanReceiveAnInlineAnswerWithoutOpeningAnotherConversation() {
        show()
        compose.onNodeWithText("交互").assertDoesNotExist()
        compose.onNodeWithText("请用中文说明你的要求。").assertExists()
        compose.onNodeWithTag("chat-composer").assertIsEnabled().performTextInput("先部署测试环境")
        compose.onNodeWithContentDescription("发送").performClick()
        assertEquals(listOf("先部署测试环境"), sent)
        assertEquals(0, newChats)
        compose.onNodeWithContentDescription("添加图片").assertIsNotEnabled()
    }

    @Test fun transientRedrawDoesNotDisableTypingAndRejectedSendRetainsTheDraft() {
        state.value = state.value.copy(interactionReady = false)
        show()
        val field = compose.onNodeWithTag("chat-composer")
        field.assertIsEnabled().performTextInput("保留这段回答")
        compose.onNodeWithContentDescription("发送").performClick()
        assertTrue(sent.isEmpty())
        field.assertTextContains("保留这段回答")
        compose.runOnIdle { state.value = state.value.copy(interactionReady = true) }
        compose.onNodeWithContentDescription("发送").performClick()
        assertEquals(listOf("保留这段回答"), sent)
    }
}
