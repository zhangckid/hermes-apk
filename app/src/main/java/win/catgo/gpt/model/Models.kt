package win.catgo.gpt.model

import win.catgo.gpt.i18n.t
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServerConfig(
    val host: String,
    val port: Int,
    val username: String,
) {
    companion object {
        const val EXAMPLE_HOST = "hermes-agent.nousresearch.com"
    }

    private val parsed: HttpUrl?
        get() {
            val raw = host.trim()
            if (raw.isEmpty() || raw.any { it.isWhitespace() } || raw.contains('\\')) return null
            val address = when {
                raw.contains("://") -> raw
                raw.count { it == ':' } > 1 && !raw.startsWith("[") -> "https://[" + raw + "]"
                else -> "https://" + raw
            }
            return address.toHttpUrlOrNull()?.takeIf {
                it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null
            }
        }

    val normalizedHost: String get() = parsed?.host.orEmpty()
    val baseUrl: String get() = HttpUrl.Builder().scheme("https").host(normalizedHost).port(port).build().toString().removeSuffix("/")

    fun validate(): String? = when {
        host.isBlank() -> t("请输入服务器地址")
        host.trim().any { it.isWhitespace() } -> t("服务器地址不能包含空格")
        parsed == null -> t("请输入有效的 HTTPS 服务器地址")
        port !in 1..65535 -> t("端口必须在 1 到 65535 之间")
        username.isBlank() -> t("请输入用户名")
        else -> null
    }
}

@Serializable
data class LoginRequest(
    val provider: String = "basic",
    val username: String,
    val password: String,
    val next: String = "/chat",
)

@Serializable
data class SessionListResponse(
    val sessions: List<SessionSummary> = emptyList(),
)

@Serializable
data class SessionSummary(
    val id: String,
    val title: String? = null,
    val preview: String? = null,
    val model: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("last_active") val lastActive: Double? = null,
    @SerialName("last_activity_at") val lastActivityAt: Double? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    val unread: Boolean = false,
    val profile: String? = null,
)

@Serializable
data class MessageListResponse(
    @SerialName("session_id") val sessionId: String,
    val messages: List<HermesMessage> = emptyList(),
)

@Serializable
data class HermesMessage(
    val id: Long? = null,
    val role: String,
    val content: String = "",
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_name") val toolName: String? = null,
    val timestamp: Double? = null,
    val pending: Boolean = false,
) {
    val stableKey: String get() = id?.toString() ?: "pending-${timestamp ?: content.hashCode()}"
}

@Serializable
data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: ToolFunction? = null,
)

@Serializable
data class ToolFunction(
    val name: String = "tool",
    val arguments: String = "",
)

@Serializable
data class ModelInfo(
    val model: String = "",
    val provider: String = "",
    val capabilities: ModelCapabilities? = null,
)

@Serializable
data class ModelCapabilities(
    @SerialName("supports_tools") val supportsTools: Boolean = false,
    @SerialName("supports_vision") val supportsVision: Boolean = false,
    @SerialName("supports_reasoning") val supportsReasoning: Boolean = false,
)

@Serializable
data class ImageUploadRequest(
    @SerialName("data_url") val dataUrl: String,
    val filename: String? = null,
)

@Serializable
data class PersistedCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

data class PickedImage(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class ToolPayload(
    val raw: JsonElement? = null,
)
