package win.catgo.gpt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import win.catgo.gpt.i18n.t

@Composable
fun VoiceInputButton(enabled: Boolean, onRecognized: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, enabled = enabled) {
        Icon(Icons.Default.Mic, contentDescription = t("语音输入"))
    }
    if (open && enabled) VoiceInputDialog(
        onDismiss = { open = false },
        onRecognized = { text -> open = false; onRecognized(text) },
    )
    LaunchedEffect(enabled) { if (!enabled) open = false }
}

/** System recognizers are not even composed/created until the user explicitly chooses one. */
@Composable
internal fun VoiceInputDialog(
    onDismiss: () -> Unit,
    onRecognized: (String) -> Unit,
    offline: @Composable ((String) -> Unit) -> Unit = { result ->
        OfflineVoiceInputButton(true, result, autoStart = true, showStatus = true)
    },
    system: @Composable ((String) -> Unit) -> Unit = { result ->
        SystemVoiceInputButton(true, result, autoStart = true)
    },
) {
    // Every new window starts offline. No silent fallback to a network-backed recognition service.
    var useSystem by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(t(if (useSystem) "系统语音输入" else "离线语音输入")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(t(if (useSystem) "系统语音可能联网；识别结果将自动发送。" else
                    "默认在本机离线识别，识别结果将自动发送。首次使用需要下载模型。"))
                key(useSystem) { if (useSystem) system(onRecognized) else offline(onRecognized) }
                TextButton(onClick = { useSystem = !useSystem }, modifier = Modifier.testTag("voice-switch-engine")) {
                    Text(t(if (useSystem) "返回离线语音" else "尝试系统语音"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("关闭")) } },
    )
}
