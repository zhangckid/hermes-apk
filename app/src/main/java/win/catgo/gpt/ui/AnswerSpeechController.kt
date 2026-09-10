package win.catgo.gpt.ui

import win.catgo.gpt.i18n.t
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Owns installed engines, language negotiation and playback lifecycle. */
internal class AnswerSpeechController(
    private val context: Context,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var disposed = false

    suspend fun speak(request: SpeechRequest) {
        engine?.stop()
        val locale = if (request.text.any { it.code in 0x3400..0x9FFF })
            Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
        var active = engine ?: initialize(null)
        val alternatives = active?.engines.orEmpty().map { it.name }.distinct()
        if (active == null || !supports(active, locale)) {
            active?.shutdown()
            active = null
            for (name in alternatives) {
                val candidate = initialize(name) ?: continue
                if (supports(candidate, locale)) {
                    active = candidate
                    break
                }
                candidate.shutdown()
            }
        }
        if (disposed) { active?.shutdown(); return }
        engine = active
        if (active == null) {
            onError(t("没有可用的朗读语音，请在系统文字转语音设置中安装相应语言的语音包。"))
            return
        }
        active.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = Unit
            @Deprecated("Required by platform")
            override fun onError(utteranceId: String?) {
                main.post { if (!disposed) onError(t("朗读失败，请检查系统语音包或网络连接。")) }
            }
        })
        val voice = active.voices.orEmpty()
            .filter { it.locale.language == locale.language &&
                !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { -it.quality }))
            .firstOrNull()
        if (voice != null) active.voice = voice
        SpeechText.chunks(request.text, minOf(TextToSpeech.getMaxSpeechInputLength(), 3500))
            .forEachIndexed { index, chunk ->
                if (active.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                        null, "catgo-${request.id}-$index") == TextToSpeech.ERROR) {
                    onError(t("语音引擎无法朗读，请检查已安装的语音数据。"))
                    return
                }
            }
    }

    private fun supports(tts: TextToSpeech, locale: Locale): Boolean =
        tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE

    private suspend fun initialize(name: String?): TextToSpeech? = withTimeoutOrNull(6000) {
        suspendCancellableCoroutine { continuation ->
            var created: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                main.post {
                    val value = created
                    if (continuation.isActive && !disposed && status == TextToSpeech.SUCCESS) {
                        continuation.resume(value)
                    } else {
                        value?.shutdown()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
            created = if (name == null) TextToSpeech(context, listener) else TextToSpeech(context, listener, name)
            continuation.invokeOnCancellation { main.post { created?.shutdown() } }
        }
    }

    fun close() {
        disposed = true
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
