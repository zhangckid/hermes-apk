package win.catgo.gpt.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import win.catgo.gpt.i18n.UiText
import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.ServerConfig
import win.catgo.gpt.network.ConnectionState
import win.catgo.gpt.ui.theme.CatgoTheme

/** Real Compose UI rendering, with local demo messages; no live backend, credentials or microphone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadmeScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun language() { UiText.initialize(ApplicationProvider.getApplicationContext()) }

    private fun screenshot(name: String) {
        val folder = System.getenv("CATGO_SCREENSHOT_DIR")?.let(::File) ?: return
        check(folder.isDirectory || folder.mkdirs())
        compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        File(folder, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun loginShowsAnEmptyEditableHostAndGreyExample() {
        compose.setContent { CatgoTheme { LoginScreen(null, false, SnackbarHostState(), { _, _ -> }) } }
        compose.onNodeWithTag("login-server").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText(ServerConfig.EXAMPLE_HOST).assertExists()
        compose.onNodeWithText("Server").assertExists()
        screenshot("login.png")
    }

    @Test fun savedServerIsNotReplacedByTheExample() {
        compose.setContent { CatgoTheme { LoginScreen(ServerConfig("family.example.com", 8443, "demo"),
            false, SnackbarHostState(), { _, _ -> }) } }
        compose.onNodeWithTag("login-server").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("family.example.com")))
        compose.onNodeWithText(ServerConfig.EXAMPLE_HOST).assertDoesNotExist()
    }

    @Test fun chatRendersDetailedDemoResponseWithNativeComposer() {
        val state = AppUiState(destination = Destination.CHAT, currentSessionId = "readme-demo",
            currentTitle = "Weekend with the family", connectionState = ConnectionState.OPEN, ptyReady = true,
            messages = listOf(
                HermesMessage(id = 1, role = "user", content = "Help us plan a relaxed weekend together."),
                HermesMessage(id = 2, role = "assistant", content = "## A little time together\n\nHere is a simple plan for the whole family:\n\n- **Saturday morning:** breakfast and a walk in the park.\n- **Saturday afternoon:** cook something new together.\n- **Sunday:** choose a film and leave time to rest.\n\nTell me everyone's preferences and I can adjust the plan.")))
        compose.setContent { CatgoTheme {
            ChatScreen(state, SnackbarHostState(), onNewChat = {}, onModelSettings = {},
                onPromptChoice = { _, _ -> false }, onPromptConfirm = { false },
                onPromptAnswer = { _, _ -> false }, onPromptSkip = { false }, onOpenSession = {},
                onSend = { false }, onStop = {}, onAddImages = {}, onRemoveImage = {},
                onReconnect = {}, onEditConnection = {}, onLogout = {}, onRefreshSessions = {},
                onVoiceRepliesChanged = {})
        } }
        compose.onNodeWithTag("chat-composer").assertIsDisplayed()
        screenshot("chat.png")
    }
}
