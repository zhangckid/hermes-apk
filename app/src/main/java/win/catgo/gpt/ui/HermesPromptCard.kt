package win.catgo.gpt.ui

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import win.catgo.gpt.i18n.t
import win.catgo.gpt.network.HermesInteractionKind
import win.catgo.gpt.network.HermesPrompt
import win.catgo.gpt.network.HermesPromptInput
import win.catgo.gpt.model.PromptExchange

@Composable
internal fun SecurePromptEffect(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .take(12).filterIsInstance<Activity>().firstOrNull() }
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        val alreadySecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (enabled && !alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable
internal fun HermesPromptCard(
    prompt: HermesPrompt,
    enabled: Boolean,
    busy: Boolean,
    onChoice: (Long, String) -> Boolean,
    onConfirm: (Long) -> Boolean,
    onAnswer: (Long, String) -> Boolean,
    onSkip: (Long) -> Boolean,
) {
    val active = enabled && !busy
    // Never save secrets into instance state, transcript, clipboard or a ViewModel.
    var secret by remember(prompt.id) { mutableStateOf("") }
    val sendSecret = { if (active && onAnswer(prompt.id, secret)) secret = "" }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("hermes-prompt")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t(when (prompt.kind) {
                HermesInteractionKind.QUESTION -> "Hermes 想问你"
                HermesInteractionKind.APPROVAL -> "请确认这条命令"
                HermesInteractionKind.SECRET -> "Hermes 需要密码或密钥"
            }), style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(prompt.body) }
            if (!prompt.complete) Text(t("正在核对完整请求，暂不能提交；若持续如此，请在网页版处理。"),
                style = MaterialTheme.typography.bodySmall)
            if (prompt.truncated) Text(t("服务器返回的内容被截断，暂不允许授权或回答。"),
                color = MaterialTheme.colorScheme.error)
            if (busy) Text(t("已提交，等待 Hermes 确认…"), style = MaterialTheme.typography.bodySmall)
            when (prompt.kind) {
                HermesInteractionKind.SECRET -> {
                    OutlinedTextField(secret, { secret = it }, singleLine = true, enabled = active,
                        label = { Text(t("密码或密钥（不保存到聊天）")) },
                        modifier = Modifier.fillMaxWidth().semantics { password() }.testTag("prompt-secret"),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendSecret() }))
                    Row {
                        Button(onClick = { sendSecret() }, enabled = active && secret.isNotBlank(),
                            modifier = Modifier.testTag("prompt-secret-send")) { Text(t("提交密码")) }
                        TextButton(onClick = { if (onSkip(prompt.id)) secret = "" }, enabled = active) { Text(t("跳过密码请求")) }
                    }
                }
                else -> {
                    prompt.choices.filter { prompt.kind != HermesInteractionKind.APPROVAL ||
                        it.label in listOf("Allow once", "Deny", "Show full command") }.forEach { choice ->
                        OutlinedButton(onClick = { onChoice(prompt.id, choice.key) }, modifier = Modifier.fillMaxWidth()
                            .testTag("prompt-choice-" + choice.key), enabled = active && HermesPromptInput.choice(prompt, choice.key) != null) {
                            Text((if (prompt.multiSelect) (if (choice.checked) "☑ " else "☐ ") else "") + t(choice.label))
                        }
                    }
                    if (prompt.multiSelect && !prompt.freeText) Button(onClick = { onConfirm(prompt.id) },
                        enabled = active && HermesPromptInput.confirmMultiple(prompt) != null,
                        modifier = Modifier.testTag("prompt-confirm")) { Text(t("确认选择")) }
                    if (prompt.canAnswerText) Text(t("在下方输入框直接回答即可"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun PromptExchangeItem(record: PromptExchange) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(record.question)
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium) { Text(record.answer, Modifier.padding(12.dp)) }
    }
}
