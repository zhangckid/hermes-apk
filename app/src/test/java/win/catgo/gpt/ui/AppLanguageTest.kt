package win.catgo.gpt.ui

import android.app.Application
import androidx.core.os.LocaleListCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.catgo.gpt.i18n.AppLanguage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class, qualifiers = "zh-rCN")
class AppLanguageTest {
    @Test fun firstLaunchDefaultsEnglishEvenOnChineseSystem() {
        assertEquals("en", AppLanguage.initialLocales(LocaleListCompat.getEmptyLocaleList()).toLanguageTags())
    }
    @Test fun explicitChineseAndEnglishSelectionsArePreserved() {
        for (tag in listOf("zh", "en")) {
            val saved = LocaleListCompat.forLanguageTags(tag)
            assertSame(saved, AppLanguage.initialLocales(saved))
        }
    }
}
