package win.catgo.gpt.ui

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.catgo.gpt.i18n.UiText
import win.catgo.gpt.model.ServerConfig
import win.catgo.gpt.ui.theme.CatgoTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w412dp-h892dp-xhdpi")
class LoginProtocolTest {
    @get:Rule val compose = createComposeRule()
    @Before fun setup() { UiText.initialize(ApplicationProvider.getApplicationContext()) }
    @Test fun defaultsSecureAndSwitchesStandardPortsWithWarning() {
        compose.setContent { CatgoTheme { LoginScreen(null, false, SnackbarHostState(), { _, _ -> }) } }
        compose.onNodeWithTag("login-protocol-https").assertIsSelected()
        compose.onNodeWithTag("login-http-warning").assertDoesNotExist()
        compose.onNodeWithTag("login-protocol-http").performClick()
        compose.onNodeWithTag("login-http-warning").assertIsDisplayed()
        compose.onNodeWithText("80").assertExists()
        compose.onNodeWithTag("login-protocol-https").performClick()
        compose.onNodeWithText("443").assertExists()
    }
    @Test fun restoresHttpAndPreservesCustomPort() {
        compose.setContent { CatgoTheme { LoginScreen(ServerConfig("example.com", 8080, "demo", "http"),
            false, SnackbarHostState(), { _, _ -> }) } }
        compose.onNodeWithTag("login-protocol-http").assertIsSelected()
        compose.onNodeWithText("8080").assertExists()
        compose.onNodeWithTag("login-protocol-https").performClick()
        compose.onNodeWithText("8080").assertExists()
    }
}
