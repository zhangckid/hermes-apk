package win.catgo.gpt.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class LegacyDataCleanupTest {
    @Test fun removesOnlyRetiredSettingsAndEncryptedCredentialsAndIsIdempotent() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        listOf("catgo_ssh_settings", "catgo_ssh_credentials", "catgo_settings",
            "catgo_secure_session", "catgo_secure_credentials").forEach {
            prefs(it).edit().putString("fixture", "local-test-value").commit()
        }
        repeat(2) { LegacyDataCleanup.removeSshData(context) }
        assertTrue(prefs("catgo_ssh_settings").all.isEmpty())
        assertTrue(prefs("catgo_ssh_credentials").all.isEmpty())
        listOf("catgo_settings", "catgo_secure_session", "catgo_secure_credentials").forEach {
            assertEquals("local-test-value", prefs(it).getString("fixture", null))
        }
    }
}
