package win.catgo.gpt.ui

import win.catgo.gpt.i18n.t
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import win.catgo.gpt.ui.theme.ElectricBlue

@Composable
fun OfflineVoiceInputButton(
    enabled: Boolean,
    onRecognized: (String) -> Unit,
    autoStart: Boolean = false,
    showStatus: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnRecognized by rememberUpdatedState(onRecognized)
    val controller = remember(context.applicationContext) {
        OfflineSpeechController(context.applicationContext)
    }
    var preparing by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf(false) }

    fun startOfflineRecognition() {
        if (preparing || listening) return
        scope.launch {
            preparing = true
            downloadProgress = 0
            if (!controller.isPrepared()) {
                Toast.makeText(
                    context,
                    t("首次使用，正在下载约 75 MB 的离线语音模型"),
                    Toast.LENGTH_LONG,
                ).show()
            }
            runCatching {
                controller.prepare { progress ->
                    withContext(Dispatchers.Main) { downloadProgress = progress }
                }
            }.onSuccess {
                preparing = false
                listening = true
                val started = controller.start(
                    onText = { text ->
                        scope.launch {
                            preparing = false
                            listening = false
                            if (currentEnabled && text.isNotBlank()) currentOnRecognized(text)
                        }
                    },
                    onError = { message ->
                        scope.launch {
                            preparing = false
                            listening = false
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onFinished = { scope.launch { preparing = false; listening = false } },
                )
                if (started) {
                    Toast.makeText(context, t("离线语音识别已开始"), Toast.LENGTH_SHORT).show()
                } else {
                    preparing = false
                    listening = false
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                preparing = false
                Toast.makeText(
                    context,
                    t("高精度离线模型尚未准备好"),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startOfflineRecognition()
        } else {
            Toast.makeText(context, t("需要麦克风权限才能使用语音输入"), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(autoStart) {
        if (autoStart && enabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                startOfflineRecognition()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        if (showStatus) Text(when {
            listening -> t("正在离线聆听，点击停止后识别")
            preparing && downloadProgress > 0 -> t("正在下载离线模型") + " " + downloadProgress + "%"
            preparing -> t("正在准备或识别语音…")
            else -> t("点击麦克风开始离线识别")
        })
        IconButton(
            onClick = {
                when {
                    preparing -> Unit
                    listening -> {
                        controller.stop()
                        listening = false
                        preparing = true
                        Toast.makeText(context, t("正在识别完整语句"), Toast.LENGTH_SHORT).show()
                    }
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> startOfflineRecognition()
                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = enabled,
        ) {
            when {
                preparing -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = ElectricBlue,
                )
                listening -> Icon(
                    Icons.Default.Stop,
                    contentDescription = t("停止离线语音输入"),
                    tint = ElectricBlue,
                )
                else -> Icon(
                    Icons.Default.Mic,
                    contentDescription = if (downloadProgress > 0) {
                        t("离线语音输入") + " ($downloadProgress%)"
                    } else {
                        t("离线语音输入")
                    },
                    tint = Color.Unspecified,
                )
            }
        }
    }
}

internal class OfflineSpeechController(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .build()
    private val modelRoot = File(context.filesDir, "speech-models")
    private val modelDirectory = File(modelRoot, MODEL_FOLDER)
    private val modelFile = File(modelDirectory, "model.int8.onnx")
    private val tokensFile = File(modelDirectory, "tokens.txt")

    @Volatile private var recording = false
    @Volatile private var closed = false
    private var recognizer: OfflineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingThread: Thread? = null

    fun isPrepared(): Boolean = recognizer != null || hasCompleteModel()

    suspend fun prepare(onProgress: suspend (Int) -> Unit) {
        if (recognizer != null) return
        withContext(Dispatchers.IO) {
            preparationMutex.withLock {
                currentCoroutineContext().ensureActive()
                if (closed) throw CancellationException("Speech window closed")
                if (!hasCompleteModel()) {
                    modelDirectory.deleteRecursively()
                    downloadAndExtract(onProgress)
                }
                val loaded = try { createRecognizer() } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    modelDirectory.deleteRecursively()
                    downloadAndExtract(onProgress)
                    createRecognizer()
                }
                synchronized(this@OfflineSpeechController) {
                    if (closed) { loaded.release(); throw CancellationException("Speech window closed") }
                    recognizer = loaded
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(
        onText: (String) -> Unit,
        onError: (String) -> Unit,
        onFinished: () -> Unit,
    ): Boolean {
        if (closed) {
            onError(t("离线语音识别器已经关闭"))
            return false
        }
        val activeRecognizer = recognizer
        if (activeRecognizer == null) {
            onError(t("高精度离线模型尚未准备好"))
            return false
        }
        if (recordingThread?.isAlive == true) {
            onError(t("上一段语音仍在处理中，请稍候"))
            return false
        }

        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBuffer <= 0) {
            onError(t("设备无法创建 16 kHz 语音录音"))
            return false
        }

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimumBuffer * 2, CAPTURE_BUFFER_SAMPLES * 2),
            )
        }.getOrElse {
            onError(t("麦克风初始化失败"))
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError(t("麦克风初始化失败"))
            return false
        }

        audioRecord = recorder
        noiseSuppressor = runCatching {
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(recorder.audioSessionId) else null
        }.getOrNull()
        recording = true
        return runCatching {
            recorder.startRecording()
            recordingThread = Thread({
                captureAndRecognize(recorder, activeRecognizer, onText, onError, onFinished)
            }, "catgo-paraformer").apply {
                isDaemon = true
                start()
            }
            true
        }.getOrElse {
            recording = false
            releaseAudio(recorder)
            onError(t("录音失败"))
            false
        }
    }

    fun stop() {
        recording = false
    }

    fun close() {
        closed = true
        client.dispatcher.cancelAll()
        recording = false
        runCatching { audioRecord?.stop() }
        recordingThread?.interrupt()
        if (recordingThread?.isAlive != true) releaseRecognizer()
    }

    private fun captureAndRecognize(
        recorder: AudioRecord,
        activeRecognizer: OfflineRecognizer,
        onText: (String) -> Unit,
        onError: (String) -> Unit,
        onFinished: () -> Unit,
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buffer = ShortArray(CAPTURE_BUFFER_SAMPLES)
        val captured = ShortArray(MAX_CAPTURE_SAMPLES)
        var capturedCount = 0
        var speechDetected = false
        var trailingSilenceSamples = 0
        var failure: String? = null

        try {
            while (recording && capturedCount < captured.size && !Thread.currentThread().isInterrupted) {
                val read = recorder.read(buffer, 0, minOf(buffer.size, captured.size - capturedCount))
                if (read == AudioRecord.ERROR_DEAD_OBJECT || read == AudioRecord.ERROR_INVALID_OPERATION) {
                    failure = t("麦克风录音中断")
                    break
                }
                if (read <= 0) continue

                buffer.copyInto(captured, capturedCount, 0, read)
                capturedCount += read
                var energy = 0.0
                for (index in 0 until read) {
                    val normalized = buffer[index] / 32768.0
                    energy += normalized * normalized
                }
                val rms = kotlin.math.sqrt(energy / read)
                if (rms >= SPEECH_RMS_THRESHOLD) {
                    speechDetected = true
                    trailingSilenceSamples = 0
                } else if (speechDetected) {
                    trailingSilenceSamples += read
                    if (trailingSilenceSamples >= AUTO_STOP_SILENCE_SAMPLES) break
                }
            }
        } catch (error: Exception) {
            if (recording) failure = error.message ?: t("录音失败")
        } finally {
            recording = false
            releaseAudio(recorder)
        }

        Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        if (failure != null) {
            onError(failure)
            finishWorker()
            return
        }
        if (capturedCount < MIN_CAPTURE_SAMPLES) {
            onError(t("录音时间太短，请说完后再停止"))
            finishWorker()
            return
        }

        runCatching {
            val samples = FloatArray(capturedCount) { captured[it] / 32768.0f }
            val stream = activeRecognizer.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                activeRecognizer.decode(stream)
                activeRecognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }.onSuccess { text ->
            if (text.isBlank()) {
                onError(t("没有识别到清晰语音，请靠近麦克风后重试"))
            } else {
                onText(text)
                onFinished()
            }
        }.onFailure { error ->
            onError(t("语音识别失败"))
        }
        finishWorker()
    }

    private fun finishWorker() {
        recordingThread = null
        if (closed) releaseRecognizer()
    }

    private fun releaseAudio(recorder: AudioRecord) {
        noiseSuppressor?.release()
        noiseSuppressor = null
        runCatching {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
        }
        recorder.release()
        if (audioRecord === recorder) audioRecord = null
    }

    @Synchronized
    private fun releaseRecognizer() {
        recognizer?.release()
        recognizer = null
    }

    private fun createRecognizer(): OfflineRecognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0.0f),
            modelConfig = OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(model = modelFile.absolutePath),
                tokens = tokensFile.absolutePath,
                numThreads = 2,
                provider = "cpu",
                modelType = "paraformer",
            ),
            decodingMethod = "greedy_search",
        ),
    )

    private fun hasCompleteModel(): Boolean =
        modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES && tokensFile.isFile

    private suspend fun downloadAndExtract(onProgress: suspend (Int) -> Unit) {
        modelRoot.mkdirs()
        val archive = File(context.cacheDir, "$MODEL_FOLDER.tar.bz2.part")
        val staging = File(modelRoot, "$MODEL_FOLDER-staging")
        archive.delete()
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val request = Request.Builder().url(MODEL_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(t("模型下载失败") + " (HTTP ${response.code})")
                val body = response.body ?: throw IOException(t("模型下载响应为空"))
                val expected = body.contentLength()
                var downloaded = 0L
                var reportedProgress = -1
                body.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (closed) throw CancellationException("Speech window closed")
                            val read = input.read(buffer)
                            if (read < 0) break
                            downloaded += read
                            if (downloaded > MAX_ARCHIVE_BYTES) throw IOException(t("模型文件过大"))
                            output.write(buffer, 0, read)
                            if (expected > 0) {
                                val progress = ((downloaded * 100) / expected).toInt().coerceIn(0, 100)
                                if (progress != reportedProgress) {
                                    reportedProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (closed) throw CancellationException("Speech window closed")
            if (archive.sha256() != MODEL_SHA256) throw IOException(t("模型文件校验失败"))
            extractModel(archive, staging)
            if (!File(staging, "model.int8.onnx").isFile || !File(staging, "tokens.txt").isFile) {
                throw IOException(t("模型压缩包缺少必要文件"))
            }
            modelDirectory.deleteRecursively()
            if (!staging.renameTo(modelDirectory)) throw IOException(t("无法安装离线模型"))
            onProgress(100)
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    private fun extractModel(archive: File, destination: File) {
        var extractedBytes = 0L
        val allowed = setOf("model.int8.onnx", "tokens.txt")
        TarArchiveInputStream(
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())),
        ).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val normalized = entry.name
                val relative = normalized.removePrefix("$MODEL_FOLDER/")
                if (!entry.isFile || relative !in allowed) continue
                val outputFile = File(destination, relative)
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (closed) throw CancellationException("Speech window closed")
                        val read = tar.read(buffer)
                        if (read < 0) break
                        extractedBytes += read
                        if (extractedBytes > MAX_UNCOMPRESSED_BYTES) {
                            throw IOException(t("解压后的模型过大"))
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val preparationMutex = Mutex()
        const val MODEL_FOLDER = "sherpa-onnx-paraformer-zh-small-2024-03-09"
        const val MODEL_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2"
        const val MODEL_SHA256 = "da92b3db5218c5be53aad53e57d1b6e63e7fc98a0e054fbdd6dbe18e9c6b1450"
        const val SAMPLE_RATE = 16_000
        const val CAPTURE_BUFFER_SAMPLES = 1_600
        const val MAX_CAPTURE_SAMPLES = SAMPLE_RATE * 30
        const val MIN_CAPTURE_SAMPLES = SAMPLE_RATE / 3
        const val AUTO_STOP_SILENCE_SAMPLES = SAMPLE_RATE * 3 / 2
        const val SPEECH_RMS_THRESHOLD = 0.012
        const val MIN_MODEL_BYTES = 70L * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 100L * 1024 * 1024
        const val MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024
    }
}
