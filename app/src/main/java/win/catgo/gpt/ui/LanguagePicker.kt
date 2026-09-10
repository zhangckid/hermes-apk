package win.catgo.gpt.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun LanguagePicker() {
    val language = LocalConfiguration.current.locales[0].language
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("语言 / Language", Modifier.weight(1f))
        TextButton(onClick = {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh"))
        }) { Text(if (language == "zh") "✓ 中文" else "中文") }
        TextButton(onClick = {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }) { Text(if (language == "en") "✓ English" else "English") }
    }
}
