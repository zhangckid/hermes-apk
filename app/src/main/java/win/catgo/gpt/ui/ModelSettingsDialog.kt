package win.catgo.gpt.ui

import win.catgo.gpt.i18n.t
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import win.catgo.gpt.model.ReasoningOptions

@Composable
internal fun ModelSettingsDialog(
    state: AppUiState,
    onDismiss: () -> Unit,
    onApply: (String, String, String, Boolean) -> Unit,
    onReload: () -> Unit,
) {
    val options = state.modelOptions
    var provider by remember(options) { mutableStateOf(options?.provider.orEmpty()) }
    var model by remember(options) { mutableStateOf(options?.model.orEmpty()) }
    var reasoning by remember(state.reasoning) { mutableStateOf(state.reasoning) }
    var confirmationSelection by remember { mutableStateOf("") }
    val selection = "$provider|$model|$reasoning"
    val confirm = state.modelConfirmation && confirmationSelection == selection
    AlertDialog(
        onDismissRequest = { if (!state.modelSettingsBusy) onDismiss() },
        title = { Text(t("模型与 Reasoning")) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("设置作用于服务器 default 配置，与网页版共享。应用后会开启新对话，旧会话保留。"))
                if (state.modelSettingsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                val providers = options?.providers.orEmpty().filter { it.authenticated }
                SettingMenu(t("服务商"), provider, providers.map { it.slug }, !state.modelSettingsBusy) {
                    provider = it
                    model = providers.firstOrNull { p -> p.slug == it }?.models?.firstOrNull().orEmpty()
                }
                SettingMenu(t("模型"), model, providers.firstOrNull { it.slug == provider }?.models.orEmpty(),
                    !state.modelSettingsBusy) { model = it }
                SettingMenu("Reasoning", reasoning, ReasoningOptions.values, !state.modelSettingsBusy) { reasoning = it }
                Text(t("可用推理档位取决于模型；不支持的设置以服务器校验结果为准。"),
                    style = MaterialTheme.typography.bodySmall)
                state.modelSettingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (confirm) Text(t("服务器提示该模型需要额外费用确认。确定后才继续应用。"),
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onReload, enabled = !state.modelSettingsBusy) { Text(t("重新读取服务器设置")) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.modelSettingsBusy && provider.isNotBlank() && model.isNotBlank(),
                onClick = {
                    confirmationSelection = selection
                    onApply(provider, model, reasoning, confirm)
                },
            ) { Text(if (confirm) t("确认费用并应用") else t("应用并新建对话")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.modelSettingsBusy) { Text(t("取消")) }
        },
    )
}

@Composable
private fun SettingMenu(label: String, value: String, values: List<String>, enabled: Boolean,
    onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && values.isNotEmpty()) {
                Text(value.ifBlank { t("请选择") })
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}
