package win.catgo.gpt.network

import win.catgo.gpt.i18n.t
import android.content.Context
import android.util.Base64
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import win.catgo.gpt.data.SecureSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import win.catgo.gpt.data.EncryptedCookieJar
import win.catgo.gpt.data.SettingsStore
import win.catgo.gpt.model.HermesMessage
import win.catgo.gpt.model.ImageUploadRequest
import win.catgo.gpt.model.LoginRequest
import win.catgo.gpt.model.MessageListResponse
import win.catgo.gpt.model.ModelInfo
import win.catgo.gpt.model.PickedImage
import win.catgo.gpt.model.ServerConfig
import win.catgo.gpt.model.SessionSummary
import win.catgo.gpt.model.SessionListResponse

class HermesClient(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val cookieJar: EncryptedCookieJar,
    override val json: Json,
    private val credentials: win.catgo.gpt.data.SessionStorage = SecureSessionStore(context, "catgo_secure_credentials"),
) : HermesPtyBackend {
    private val authMutex = Mutex()
    private val authGeneration = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var cachedClient: OkHttpClient? = null
    @Volatile private var cachedClientKey: String? = null

    fun savedConfig(): ServerConfig? = settingsStore.load()

    fun hasSavedSession(): Boolean = cookieJar.hasSession() || credentials.read() != null

    fun voiceRepliesEnabled(): Boolean = settingsStore.voiceRepliesEnabled()

    fun setVoiceRepliesEnabled(enabled: Boolean) = settingsStore.setVoiceRepliesEnabled(enabled)

    fun saveConfig(config: ServerConfig) {
        val previousKey = settingsStore.load()?.clientKey()
        settingsStore.save(config)
        if (previousKey != config.clientKey()) {
            cachedClient?.dispatcher?.cancelAll()
            cookieJar.clear()
            credentials.clear()
            cachedClient = null
            cachedClientKey = null
        }
    }

    suspend fun login(config: ServerConfig, password: String) {
        config.validate()?.let { throw IllegalArgumentException(it) }
        if (password.isBlank()) throw IllegalArgumentException(t("请输入密码"))
        saveConfig(config)
        val request = Request.Builder()
            .url(url("/auth/password-login"))
            .post(
                json.encodeToString(LoginRequest(username = config.username.trim(), password = password))
                    .toRequestBody(JSON_MEDIA_TYPE),
            )
            .build()
        executeLogin(request)
        credentials.write(json.encodeToString(LoginRequest(username = config.username.trim(), password = password)))
    }

    suspend fun checkAuthenticated(): Boolean {
        execute(Request.Builder().url(url("/api/auth/me")).get().build())
        return true
    }

    suspend fun getSessions(query: String = ""): List<SessionSummary> {
        val path = if (query.isBlank()) {
            "/api/sessions?limit=50&offset=0&order=recent"
        } else {
            "/api/sessions/search?q=${encodeQuery(query)}&limit=50&order=recent"
        }
        return decode<SessionListResponse>(execute(Request.Builder().url(url(path)).get().build())).sessions
    }

    suspend fun getMessages(sessionId: String): List<HermesMessage> {
        val path = "/api/sessions/${encodePath(sessionId)}/messages?limit=500&order=latest"
        return decode<MessageListResponse>(execute(Request.Builder().url(url(path)).get().build())).messages
    }

    suspend fun getModelInfo(): ModelInfo = decode(
        execute(Request.Builder().url(url("/api/model/info")).get().build()),
    )

    suspend fun uploadImage(image: PickedImage): String {
        val encoded = Base64.encodeToString(image.bytes, Base64.NO_WRAP)
        val dataUrl = "data:${image.mimeType};base64,$encoded"
        val body = json.encodeToString(ImageUploadRequest(dataUrl = dataUrl, filename = image.displayName))
        val raw = execute(
            Request.Builder()
                .url(url("/api/chat/image-upload"))
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        return json.parseToJsonElement(raw).jsonObject["path"]?.jsonPrimitive?.content
            ?: throw IOException(t("服务器没有返回图片路径"))
    }

    override suspend fun createWebSocketTicket(): String {
        val raw = execute(
            Request.Builder()
                .url(url("/api/auth/ws-ticket"))
                .post(ByteArray(0).toRequestBody(null))
                .build(),
        )
        return json.parseToJsonElement(raw).jsonObject["ticket"]?.jsonPrimitive?.content
            ?: throw IOException(t("服务器没有返回 WebSocket 票据"))
    }

    override fun openPtySocket(
        ticket: String,
        resumeSessionId: String?,
        fresh: Boolean,
        attachId: String,
        listener: okhttp3.WebSocketListener,
    ): okhttp3.WebSocket {
        val channel = "chat-${UUID.randomUUID()}"
        val params = buildList {
            add("ticket=${encodeQuery(ticket)}")
            add("channel=${encodeQuery(channel)}")
            add("attach=${encodeQuery(attachId)}")
            add("profile=default")
            if (fresh) add("fresh=1")
            if (!resumeSessionId.isNullOrBlank()) add("resume=${encodeQuery(resumeSessionId)}")
        }.joinToString("&")
        val wsUrl = requireConfig().webSocketBaseUrl + "/api/pty?$params"
        return client().newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    suspend fun logout() {
        runCatching {
            execute(
                Request.Builder()
                    .url(url("/auth/logout"))
                    .post(ByteArray(0).toRequestBody(null))
                    .build(),
            )
        }
        cookieJar.clear()
        credentials.clear()
    }

    fun clearSession() = cookieJar.clear()

    private fun client(): OkHttpClient {
        val config = requireConfig()
        val key = config.clientKey()
        cachedClient?.takeIf { cachedClientKey == key }?.let { return it }
        return synchronized(this) {
            cachedClient?.takeIf { cachedClientKey == key } ?: OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .apply { ServerHttpPolicy.apply(this); ServerHttpPolicy.restrictToServer(this, config); if (config.scheme == "https") TlsConfigurator.apply(this, context) }
                .build()
                .also {
                    cachedClient = it
                    cachedClientKey = key
                }
        }
    }

    private suspend fun execute(request: Request): String {
        val identity = requireConfig().clientKey()
        val observedAuthGeneration = authGeneration.get()
        return try {
            executeRaw(request)
        } catch (error: HttpStatusException) {
            if (error.statusCode != 401 || !request.url.encodedPath.startsWith("/api/")) throw error
            authMutex.withLock {
                if (requireConfig().clientKey() != identity) throw kotlinx.coroutines.CancellationException()
                if (authGeneration.get() == observedAuthGeneration) {
                    val saved = credentials.read() ?: throw error
                    executeLogin(Request.Builder().url(url("/auth/password-login"))
                        .post(saved.toRequestBody(JSON_MEDIA_TYPE)).build())
                    authGeneration.incrementAndGet()
                }
            }
            if (requireConfig().clientKey() != identity) throw kotlinx.coroutines.CancellationException()
            executeRaw(request)
        }
    }

    private suspend fun executeLogin(request: Request) {
        executeRaw(request, allowLoginRedirect = true)
        if (cookieJar.requiresHttps(request.url)) {
            throw IOException(t("服务器登录凭据要求 HTTPS，请切换为 HTTPS。"))
        }
        // A 200 response or a redirect alone does not establish an authenticated session.
        executeRaw(Request.Builder().url(url("/api/auth/me")).get().build())
    }

    /** Cancellation cancels the actual HTTP call, not just its caller's UI state. */
    private suspend fun executeRaw(request: Request, allowLoginRedirect: Boolean = false): String = suspendCancellableCoroutine { continuation ->
        val call = client().newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val result = runCatching {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        val location = it.header("Location")?.let(request.url::resolve)
                        val loginRedirect = allowLoginRedirect && it.code in setOf(302, 303) &&
                            location != null && location.scheme == request.url.scheme &&
                            location.host == request.url.host && location.port == request.url.port &&
                            location.username.isEmpty() && location.password.isEmpty()
                        // Accept the login cookie, then verify /api/auth/me. Never forward the password.
                        if (it.isRedirect && !loginRedirect) throw HttpStatusException(it.code,
                            t("服务器要求重定向，请填写最终地址并确认 HTTP/HTTPS 协议。"))
                        if (!it.isSuccessful && !loginRedirect) throw HttpStatusException(it.code, when (it.code) {
                            401 -> t("用户名、密码错误或登录已过期")
                            429 -> t("尝试次数过多，请稍后重试")
                            else -> t("服务器返回错误") + " ${it.code}"
                        })
                        body
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    suspend fun getModelOptions(): win.catgo.gpt.model.ModelOptions = decode(
        execute(Request.Builder().url(url("/api/model/options?profile=default")).get().build()))

    suspend fun getReasoning(): String {
        val config = readConfig()
        return config["agent"]?.jsonObject?.get("reasoning_effort")?.jsonPrimitive?.content ?: "medium"
    }

    private suspend fun readConfig() = json.parseToJsonElement(
        execute(Request.Builder().url(url("/api/config?profile=default")).get().build())).jsonObject

    suspend fun setModel(provider: String, model: String, confirm: Boolean): Boolean {
        val body = kotlinx.serialization.json.buildJsonObject {
            put("scope", kotlinx.serialization.json.JsonPrimitive("main"))
            put("provider", kotlinx.serialization.json.JsonPrimitive(provider))
            put("model", kotlinx.serialization.json.JsonPrimitive(model))
            put("confirm_expensive_model", kotlinx.serialization.json.JsonPrimitive(confirm))
        }
        val raw = execute(Request.Builder().url(url("/api/model/set?profile=default"))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
        return json.parseToJsonElement(raw).jsonObject["confirm_required"]?.jsonPrimitive?.content != "true"
    }

    suspend fun setReasoning(reasoning: String) {
        require(reasoning in win.catgo.gpt.model.ReasoningOptions.values)
        val config = readConfig()
        val agent = config["agent"]?.jsonObject.orEmpty().toMutableMap()
        agent["reasoning_effort"] = kotlinx.serialization.json.JsonPrimitive(reasoning)
        val updated = kotlinx.serialization.json.JsonObject(config + ("agent" to kotlinx.serialization.json.JsonObject(agent)))
        val body = kotlinx.serialization.json.JsonObject(mapOf("config" to updated))
        execute(Request.Builder().url(url("/api/config?profile=default"))
            .put(body.toString().toRequestBody(JSON_MEDIA_TYPE)).build())
    }

    private inline fun <reified T> decode(raw: String): T = json.decodeFromString(raw)

    private fun url(path: String): String = requireConfig().baseUrl + path

    private fun requireConfig(): ServerConfig = settingsStore.load()
        ?: throw IllegalStateException(t("尚未配置服务器"))

    private fun ServerConfig.clientKey(): String = baseUrl + "|" + username.trim()

    private fun encodeQuery(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
        .replace("+", "%20")

    private fun encodePath(value: String): String = encodeQuery(value).replace("%2F", "/")

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class HttpStatusException(val statusCode: Int, message: String) : IOException(message)
