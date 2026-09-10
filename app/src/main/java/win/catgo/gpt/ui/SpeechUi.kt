package win.catgo.gpt.ui

import win.catgo.gpt.i18n.t
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale
import win.catgo.gpt.ui.theme.ElectricBlue

@Composable
internal fun SystemVoiceInputButton(
    enabled: Boolean,
    onRecognized: (String) -> Unit,
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    var forceOffline by rememberSaveable { mutableStateOf(false) }
    if (forceOffline || !hasSystemSpeech(context)) {
        OfflineVoiceInputButton(enabled = enabled, onRecognized = onRecognized, autoStart = autoStart, showStatus = true)
        return
    }
    val currentEnabled by rememberUpdatedState(enabled)
    var recognitionDelivered by remember { mutableStateOf(false) }
    val currentOnRecognized by rememberUpdatedState(onRecognized)
    var listening by remember { mutableStateOf(false) }
    var cancelledByUser by remember { mutableStateOf(false) }
    var fallbackRequest by remember { mutableIntStateOf(0) }
    var preferSystemPanel by rememberSaveable { mutableStateOf(false) }
    val recognizer = remember(context) { runCatching { createBestRecognizer(context) }.getOrNull() }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spoken.isNotBlank()) {
                if (currentEnabled && !recognitionDelivered) {
                    recognitionDelivered = true
                    currentOnRecognized(spoken)
                }
            } else {
                Toast.makeText(context, t("系统语音识别没有返回文字"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
            }

            override fun onBeginningOfSpeech() {
                listening = true
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                listening = false
                if (cancelledByUser) {
                    cancelledByUser = false
                    return
                }
                if (shouldUseSystemFallback(error)) {
                    preferSystemPanel = true
                    fallbackRequest += 1
                } else {
                    Toast.makeText(
                        context,
                        speechRecognitionError(error) + " (" + t("错误码") + " $error)",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (spoken.isNotBlank()) {
                    if (currentEnabled && !recognitionDelivered) {
                        recognitionDelivered = true
                        currentOnRecognized(spoken)
                    }
                } else {
                    Toast.makeText(context, t("没有识别到文字，请再试一次"), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    LaunchedEffect(fallbackRequest) {
        if (fallbackRequest == 0) return@LaunchedEffect
        val intent = speechIntent(context)
        if (intent.resolveActivity(context.packageManager) != null) {
            Toast.makeText(context, t("正在切换到系统语音识别"), Toast.LENGTH_SHORT).show()
            systemSpeechLauncher.launch(intent)
        } else {
            forceOffline = true
            Toast.makeText(
                context,
                t("系统语音服务不可用，已切换到离线识别，请再次点击麦克风"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun beginListening() {
        recognitionDelivered = false
        if (preferSystemPanel) {
            fallbackRequest += 1
            return
        }
        val activeRecognizer = recognizer
        if (activeRecognizer == null) {
            preferSystemPanel = true
            fallbackRequest += 1
            return
        }
        cancelledByUser = false
        recognitionDelivered = false
        runCatching {
            activeRecognizer.startListening(speechIntent(context))
            listening = true
        }.onFailure {
            listening = false
            preferSystemPanel = true
            fallbackRequest += 1
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            Toast.makeText(context, t("需要麦克风权限才能使用语音输入"), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(autoStart) {
        if (autoStart && enabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                beginListening()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled && listening) {
            cancelledByUser = true
            recognizer?.cancel()
            listening = false
        }
    }

    IconButton(
        onClick = {
            if (listening) {
                cancelledByUser = true
                recognizer?.cancel()
                listening = false
            } else if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                beginListening()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        enabled = enabled,
    ) {
        Icon(
            if (listening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (listening) t("停止语音输入") else t("语音输入"),
            tint = if (listening) ElectricBlue else Color.Unspecified,
        )
    }
}

@Composable
fun VoiceReplyEffect(
    enabled: Boolean,
    request: SpeechRequest?,
    onConsumed: (Long) -> Unit,
) {
    if (!enabled) return
    val context = LocalContext.current
    var speechError by remember { mutableStateOf<String?>(null) }
    val controller = remember(context) { AnswerSpeechController(context) { speechError = it } }
    val currentOnConsumed by rememberUpdatedState(onConsumed)
    DisposableEffect(controller) { onDispose { controller.close() } }
    LaunchedEffect(request?.id) {
        val active = request ?: return@LaunchedEffect
        runCatching {
            if (active.text.isNotBlank()) controller.speak(active)
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            speechError = t("语音引擎不可用，请检查系统文字转语音设置。")
        }
        currentOnConsumed(active.id)
    }
    speechError?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { speechError = null },
            title = { androidx.compose.material3.Text(t("语音回答")) },
            text = { androidx.compose.material3.Text(message) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                        .onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                    speechError = null
                }) { androidx.compose.material3.Text(t("语音设置")) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { speechError = null }) {
                    androidx.compose.material3.Text(t("关闭"))
                }
            },
        )
    }
}

private fun hasSystemSpeech(context: Context): Boolean =
    SpeechRecognizer.isRecognitionAvailable(context) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) ||
        speechIntent(context).resolveActivity(context.packageManager) != null

private fun createBestRecognizer(context: Context): SpeechRecognizer? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
    }
    SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
    else -> null
}

private fun speechIntent(context: Context): Intent {
    val language = win.catgo.gpt.i18n.UiText.locale(context).toLanguageTag()
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PROMPT, t("请说出你想发送的内容"))
    }
}

private fun shouldUseSystemFallback(code: Int): Boolean = code in setOf(
    SpeechRecognizer.ERROR_CLIENT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
)

private fun speechRecognitionError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> t("无法读取麦克风音频")
    SpeechRecognizer.ERROR_CLIENT -> t("应用内语音识别不可用")
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> t("没有麦克风权限")
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> t("语音识别网络不可用")
    SpeechRecognizer.ERROR_NO_MATCH -> t("没有听清，请再试一次")
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> t("语音识别服务正忙，请稍后重试")
    SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> t("语音识别服务暂时不可用")
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> t("没有检测到语音")
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> t("语音请求过多，请稍后重试")
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> t("系统不支持当前语言")
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> t("当前语言的识别模型尚未安装")
    else -> t("语音识别失败")
}
