package win.catgo.gpt.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.catgo.gpt.network.LocalHermesFixture

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ConnectionNavigationTest {
    @Test fun serverSettingsStayOpenWhenRestoreFinishesLate() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        try { LocalHermesFixture().use { f ->
            f.credentials.write("local-saved-credential-marker")
            val requested = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path.orEmpty().startsWith("/api/sessions?")) {
                        requested.countDown()
                        release.await(5, TimeUnit.SECONDS)
                        return MockResponse().setBody("""{"sessions":[]}""")
                    }
                    return MockResponse().setBody("{}")
                }
            }
            val vm = AppViewModel(f.context, f.client()) { "local-attach" }
            val store = ViewModelStore().apply { put("test", vm) }
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (requested.count > 0 && System.nanoTime() < deadline) { main.scheduler.runCurrent(); Thread.sleep(10) }
                assertEquals(0L, requested.count)
                vm.editConnection()
                assertEquals(Destination.LOGIN, vm.state.value.destination)
                assertFalse(vm.state.value.busy)
                release.countDown()
                repeat(30) { Thread.sleep(10); main.scheduler.runCurrent() }
                vm.onForeground()
                main.scheduler.runCurrent()
                assertEquals(Destination.LOGIN, vm.state.value.destination)
                assertEquals(f.config, vm.state.value.savedConfig)
            } finally { release.countDown(); store.clear(); main.scheduler.runCurrent() }
        } } finally { Dispatchers.resetMain() }
    }

    @Test fun openingSettingsCancelsAnUnresponsiveManualLogin() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        try { LocalHermesFixture().use { f ->
            f.server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
            val vm = AppViewModel(f.context, f.client()) { "local-attach" }
            val store = ViewModelStore().apply { put("test", vm) }
            try {
                main.scheduler.runCurrent()
                vm.login(f.config, "fake-local-password")
                main.scheduler.runCurrent()
                assertTrue(vm.state.value.busy)
                assertNotNull(f.server.takeRequest(3, TimeUnit.SECONDS))
                vm.editConnection()
                main.scheduler.runCurrent()
                assertEquals(Destination.LOGIN, vm.state.value.destination)
                assertFalse(vm.state.value.busy)
                assertNull(vm.state.value.error)
                assertEquals(f.config, vm.state.value.savedConfig)
            } finally { store.clear(); main.scheduler.runCurrent() }
        } } finally { Dispatchers.resetMain() }
    }

    @Test fun failedRestoreShowsLoginErrorInsteadOfSilentReconnect() {
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        try { LocalHermesFixture().use { f ->
            f.credentials.write("local-saved-credential-marker")
            f.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(403)
            }
            val vm = AppViewModel(f.context, f.client()) { "local-attach" }
            val store = ViewModelStore().apply { put("test", vm) }
            try {
                main.scheduler.runCurrent()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (vm.state.value.error == null && System.nanoTime() < deadline) { Thread.sleep(10); main.scheduler.runCurrent() }
                assertEquals(Destination.LOGIN, vm.state.value.destination)
                assertTrue(vm.state.value.error.orEmpty().contains("403"))
                assertFalse(vm.state.value.busy)
                assertEquals(f.config, vm.state.value.savedConfig)
            } finally { store.clear(); main.scheduler.runCurrent() }
        } } finally { Dispatchers.resetMain() }
    }
}
