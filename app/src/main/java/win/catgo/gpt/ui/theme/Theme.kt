package win.catgo.gpt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val InkBlack = Color(0xFF171715)
val WarmSurface = Color(0xFF22221F)
val RaisedSurface = Color(0xFF2B2B27)
val PaperWhite = Color(0xFFF1EFE7)
val MutedText = Color(0xFFAAA89F)
val ElectricBlue = Color(0xFF6772FF)
val Clay = Color(0xFFD97757)
val Hairline = Color(0xFF3A3934)

private val CatgoDarkColors = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2933B5),
    onPrimaryContainer = Color(0xFFE1E3FF),
    secondary = Clay,
    onSecondary = Color(0xFF24120C),
    background = InkBlack,
    onBackground = PaperWhite,
    surface = WarmSurface,
    onSurface = PaperWhite,
    surfaceVariant = RaisedSurface,
    onSurfaceVariant = MutedText,
    outline = Hairline,
    error = Color(0xFFFF8A80),
)

@Composable
fun CatgoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CatgoDarkColors,
        typography = MaterialTheme.typography.copy(
            titleLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 23.sp,
                lineHeight = 28.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        ),
        content = content,
    )
}
