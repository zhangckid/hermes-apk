package win.catgo.gpt.ui

import android.app.Application
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class VoiceInputDialogTest {
    @get:Rule val compose = createComposeRule()
    private val open = mutableStateOf(true)
    private var systemCreations = 0
    private val results = mutableListOf<String>()
    private fun show() {
        compose.setContent { MaterialTheme {
            if (open.value) VoiceInputDialog(onDismiss = { open.value = false },
                onRecognized = { results.add(it); open.value = false },
                offline = { result -> Button(onClick = { result("离线中文") }) { Text("test-offline") } },
                system = { result ->
                    DisposableEffect(Unit) { systemCreations++; onDispose {} }
                    Button(onClick = { result("system result") }) { Text("test-system") }
                })
        } }
    }

    @Test fun defaultsOfflineAndOnlyCreatesSystemAfterExplicitChoice() {
        show()
        compose.onNodeWithText("test-offline").assertExists()
        compose.onNodeWithText("test-system").assertDoesNotExist()
        assertEquals(0, systemCreations)
        compose.onNodeWithTag("voice-switch-engine").performClick()
        compose.onNodeWithText("test-system").assertExists()
        assertEquals(1, systemCreations)
        assertTrue(results.isEmpty())
    }

    @Test fun reopeningAlwaysReturnsToOffline() {
        show()
        compose.onNodeWithTag("voice-switch-engine").performClick()
        compose.onNodeWithText(win.catgo.gpt.i18n.t("关闭")).performClick()
        compose.runOnIdle { open.value = true }
        compose.onNodeWithText("test-offline").assertExists()
        compose.onNodeWithText("test-system").assertDoesNotExist()
        assertEquals(1, systemCreations)
    }

    @Test fun offlineResultIsDeliveredAndWindowClosesWithoutTryingSystem() {
        show()
        compose.onNodeWithText("test-offline").performClick()
        assertEquals(listOf("离线中文"), results)
        compose.onNodeWithText("test-offline").assertDoesNotExist()
        assertEquals(0, systemCreations)
    }
}
