package win.catgo.gpt.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

internal object AppLanguage {
    fun initialLocales(saved: LocaleListCompat): LocaleListCompat =
        if (saved.isEmpty) LocaleListCompat.forLanguageTags("en") else saved

    /** Called after AppCompat restores any explicit per-app language preference. */
    fun ensureDefault() {
        val saved = AppCompatDelegate.getApplicationLocales()
        if (saved.isEmpty) AppCompatDelegate.setApplicationLocales(initialLocales(saved))
    }
}
