package win.catgo.gpt.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.catgo.gpt.network.PromptFixtures
import win.catgo.gpt.network.HermesPrompt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class HermesPromptCardTest {
    @get:Rule val compose = createComposeRule()
    private val choices = mutableListOf<String>()
    private val secrets = mutableListOf<String>()
    private var confirms = 0
    private fun show(prompt: HermesPrompt, enabled: Boolean = true, busy: Boolean = false) {
        compose.setContent { MaterialTheme {
            HermesPromptCard(prompt.copy(id = 42), enabled, busy,
                { id, key -> assertEquals(42, id); choices.add(key) }, { confirms++; true },
                { _, text -> secrets.add(text) }, { true })
        } }
    }

    @Test fun questionDisplaysInlineAndChoiceClickDoesNotOpenATerminal() {
        show(PromptFixtures.parse(PromptFixtures.question()))
        compose.onNodeWithText("你希望部署到哪里？").assertIsDisplayed()
        compose.onNodeWithTag("prompt-choice-2").performClick()
        assertEquals(listOf("2"), choices)
        compose.onNodeWithText("交互").assertDoesNotExist()
        compose.onNodeWithText("终端不可用，请返回配置页重新连接").assertDoesNotExist()
    }

    @Test fun approvalRequiresExplicitClickAndNeverOffersPermanentPermission() {
        show(PromptFixtures.parse(PromptFixtures.approval()))
        assertTrue(choices.isEmpty())
        compose.onNodeWithText("sudo systemctl status hermes").assertIsDisplayed()
        compose.onNodeWithTag("prompt-choice-2").assertDoesNotExist()
        compose.onNodeWithTag("prompt-choice-3").assertDoesNotExist()
        compose.onNodeWithTag("prompt-choice-1").performClick()
        assertEquals(listOf("1"), choices)
    }

    @Test fun truncatedApprovalDisablesAllowButKeepsDenyAndShowFull() {
        show(PromptFixtures.parse(PromptFixtures.approval("sudo … (choose Show full command)")))
        compose.onNodeWithTag("prompt-choice-1").assertIsNotEnabled()
        compose.onNodeWithTag("prompt-choice-4").assertIsEnabled()
        compose.onNodeWithTag("prompt-choice-5").performClick()
        assertEquals(listOf("5"), choices)
    }

    @Test fun disconnectedAndAlreadySubmittedPromptsCannotBeClickedAgain() {
        show(PromptFixtures.parse(PromptFixtures.question()), enabled = false, busy = true)
        compose.onNodeWithTag("prompt-choice-1").assertIsNotEnabled()
        assertTrue(choices.isEmpty())
    }

    @Test fun passwordIsMaskedAndOnlySentByItsDedicatedInput() {
        show(PromptFixtures.parse(PromptFixtures.secret()))
        val field = compose.onNodeWithTag("prompt-secret")
        field.performTextInput("fake-only")
        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertTrue(secrets.isEmpty())
        field.performImeAction()
        assertEquals(listOf("fake-only"), secrets)
        compose.onNodeWithTag("prompt-secret-send").assertIsNotEnabled()
    }

    @Test fun multiSelectionNeedsAnExplicitConfirmation() {
        show(PromptFixtures.parse(PromptFixtures.question(multi = true).replace("[ ] 1.", "[x] 1.")))
        assertEquals(0, confirms)
        compose.onNodeWithTag("prompt-confirm").performClick()
        assertEquals(1, confirms)
    }
}
