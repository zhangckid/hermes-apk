package win.catgo.gpt.network

import android.app.Application
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HermesHttpLoginTest {
    @Test fun httpLoginRestoresCookiesLoadsSessionsAndConnectsWs() = runBlocking {
        for (loginStatus in listOf(200, 302, 303)) LocalHermesFixture().use { f ->
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    if (path == "/auth/password-login") return MockResponse().setResponseCode(loginStatus)
                        .addHeader("Set-Cookie", "hermes_session_local=fixture; Path=/; HttpOnly")
                        .addHeader("Location", "/chat").setBody("{}")
                    if (request.getHeader("Cookie") != "hermes_session_local=fixture") return MockResponse().setResponseCode(401)
                    return when {
                        path == "/api/auth/me" -> MockResponse().setBody("{}")
                        path.startsWith("/api/sessions?") -> MockResponse().setBody("""{"sessions":[{"id":"demo"}]}""")
                        path == "/api/auth/ws-ticket" -> MockResponse().setBody("""{"ticket":"local-ticket"}""")
                        path.startsWith("/api/pty?") -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("ready") }
                        })
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            f.client().login(f.config, "fake-local-password")
            assertNotNull(f.credentials.read())
            val restored = f.client()
            assertTrue(restored.checkAuthenticated())
            assertEquals("demo", restored.getSessions().single().id)
            val ready = CountDownLatch(1)
            val message = AtomicReference<String>()
            val socket = restored.openPtySocket(restored.createWebSocketTicket(), null, true, "local-attach",
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) { message.set(text); ready.countDown() }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { ready.countDown() }
                })
            try { assertTrue(ready.await(5, TimeUnit.SECONDS)); assertEquals("ready", message.get()) }
            finally { socket.cancel() }
        }
    }

    @Test fun secureCookieOverHttpReportsHttpsRequirementWithoutSavingPassword() = runBlocking {
        LocalHermesFixture().use { f ->
            f.server.enqueue(MockResponse().addHeader("Set-Cookie", "hermes_session_local=fixture; Path=/; Secure; HttpOnly").setBody("{}"))
            try { f.client().login(f.config, "fake-local-password"); fail("Secure cookie cannot authenticate over HTTP") }
            catch (expected: IOException) { assertTrue(expected.message.orEmpty().contains("HTTPS")) }
            assertNull(f.credentials.read())
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test fun loginRedirectCannotForwardCredentialsToDifferentOrigin() = runBlocking {
        LocalHermesFixture().use { f ->
            f.server.enqueue(MockResponse().setResponseCode(303).addHeader("Location", "https://example.com/chat"))
            try { f.client().login(f.config, "fake-local-password"); fail("Cross-origin redirect must fail") }
            catch (expected: HttpStatusException) { assertEquals(303, expected.statusCode) }
            assertNull(f.credentials.read())
            assertEquals(1, f.server.requestCount)
        }
    }

    @Test fun successfulPostWithoutAuthenticatedSessionDoesNotSavePassword() = runBlocking {
        LocalHermesFixture().use { f ->
            f.server.enqueue(MockResponse().setBody("{}"))
            f.server.enqueue(MockResponse().setResponseCode(401))
            try { f.client().login(f.config, "fake-local-password"); fail("Unauthenticated session must fail") }
            catch (expected: HttpStatusException) { assertEquals(401, expected.statusCode) }
            assertNull(f.credentials.read())
            assertEquals(2, f.server.requestCount)
        }
    }
}
