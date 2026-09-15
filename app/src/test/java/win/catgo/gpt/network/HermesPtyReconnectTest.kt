package win.catgo.gpt.network

import android.app.Application
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HermesPtyReconnectTest {
    @Test fun reconnectKeepsChannelWhileAnotherConversationGetsAnIndependentChannel() {
        LocalHermesFixture().use { f ->
            val client = f.client()
            fun connect(attach: String, resume: String?, fresh: Boolean): HttpUrl {
                f.server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
                val opened = CountDownLatch(1)
                val socket = client.openPtySocket("test-ticket", resume, fresh, attach,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) { opened.countDown() }
                    })
                try {
                    assertTrue(opened.await(5, TimeUnit.SECONDS))
                    return requireNotNull(f.server.takeRequest(5, TimeUnit.SECONDS)?.requestUrl)
                } finally { socket.cancel() }
            }
            val initial = connect("conversation-a", null, true)
            val resumed = connect("conversation-a", "session-a", false)
            val retry = connect("conversation-a", "session-a", false)
            val another = connect("conversation-b", null, true)
            assertEquals(initial.queryParameter("channel"), resumed.queryParameter("channel"))
            assertEquals(resumed.queryParameter("channel"), retry.queryParameter("channel"))
            assertNotEquals(initial.queryParameter("channel"), another.queryParameter("channel"))
            assertEquals("conversation-a", resumed.queryParameter("attach"))
            assertNull(resumed.queryParameter("resume"))
            assertNull(resumed.queryParameter("fresh"))
            assertEquals("1", initial.queryParameter("fresh"))
        }
    }
}
