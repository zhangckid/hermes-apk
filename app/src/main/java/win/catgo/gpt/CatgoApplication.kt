package win.catgo.gpt

import android.app.Application

class CatgoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        win.catgo.gpt.data.LegacyDataCleanup.removeSshData(this)
        win.catgo.gpt.i18n.UiText.initialize(this)
    }
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
