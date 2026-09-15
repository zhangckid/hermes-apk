package win.catgo.gpt.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import win.catgo.gpt.model.PersistedCookie

class EncryptedCookieJar(
    private val secureStore: SessionStorage,
    private val json: Json,
) : CookieJar {
    private val lock = Any()
    private var cookies: MutableList<Cookie> = restore().toMutableList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        val now = System.currentTimeMillis()
        this.cookies.removeAll { it.expiresAt <= now }
        cookies.forEach { incoming ->
            this.cookies.removeAll {
                it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
            }
            if (incoming.expiresAt > now) this.cookies += incoming
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val removed = cookies.removeAll { it.expiresAt <= now }
        if (removed) persist()
        cookies.filter { it.matches(url) }
    }

    fun hasSession(): Boolean = synchronized(lock) {
        cookies.any { it.name.startsWith("hermes_session_") && it.expiresAt > System.currentTimeMillis() }
    }

    fun requiresHttps(url: HttpUrl): Boolean = synchronized(lock) {
        val secureUrl = url.newBuilder().scheme("https").build()
        !url.isHttps && cookies.any {
            it.name.startsWith("hermes_session_") && it.secure && it.expiresAt > System.currentTimeMillis() && it.matches(secureUrl)
        } && cookies.none {
            it.name.startsWith("hermes_session_") && it.expiresAt > System.currentTimeMillis() && it.matches(url)
        }
    }

    fun clear() = synchronized(lock) {
        cookies.clear()
        secureStore.clear()
    }

    private fun persist() {
        val persisted = cookies.map { cookie ->
            PersistedCookie(
                name = cookie.name,
                value = cookie.value,
                expiresAt = cookie.expiresAt,
                domain = cookie.domain,
                path = cookie.path,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly,
            )
        }
        secureStore.write(json.encodeToString(persisted))
    }

    private fun restore(): List<Cookie> {
        val raw = secureStore.read() ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<PersistedCookie>>(raw).mapNotNull { saved ->
                runCatching {
                    Cookie.Builder()
                        .name(saved.name)
                        .value(saved.value)
                        .expiresAt(saved.expiresAt)
                        .apply {
                            if (saved.hostOnly) hostOnlyDomain(saved.domain) else domain(saved.domain)
                            path(saved.path)
                            if (saved.secure) secure()
                            if (saved.httpOnly) httpOnly()
                        }
                        .build()
                }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }
}
