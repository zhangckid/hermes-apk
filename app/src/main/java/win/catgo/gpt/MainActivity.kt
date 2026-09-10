package win.catgo.gpt

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import win.catgo.gpt.ui.AppViewModel
import win.catgo.gpt.ui.AppViewModelFactory
import win.catgo.gpt.ui.CatgoApp
import win.catgo.gpt.ui.theme.CatgoTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        win.catgo.gpt.i18n.AppLanguage.ensureDefault()
        enableEdgeToEdge()
        val container = (application as CatgoApplication).container
        setContent {
            CatgoTheme {
                val appViewModel: AppViewModel = viewModel(
                    factory = AppViewModelFactory(application, container),
                )
                val state by appViewModel.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(appViewModel) {
                    appViewModel.onForeground()
                    onPauseOrDispose { appViewModel.onBackground() }
                }
                CatgoApp(state = state, viewModel = appViewModel)
            }
        }
    }
}
