package app.scrapbook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every screen reads these tokens, so accent, type and corners are user-customizable. */
data class SbTheme(val name: String, val acc: Color, val tint: Color, val paper: Color,
                   val serif: Boolean = true, val radius: Dp = 14.dp)

val Themes = listOf(
    SbTheme("Sage", Color(0xFF5F7F66), Color(0xFFE3EBE0), Color(0xFFF8F6F0)),
    SbTheme("Blue", Color(0xFF4F6F8D), Color(0xFFDDE7F0), Color(0xFFF4F6F8)),
    SbTheme("Butter", Color(0xFFA8802A), Color(0xFFF4E9C8), Color(0xFFFAF7EC)),
    SbTheme("Rose", Color(0xFFA0566A), Color(0xFFF2DFE3), Color(0xFFFAF5F5)),
    SbTheme("Ink", Color(0xFF24272A), Color(0xFFE6E6E3), Color(0xFFF7F7F5)),
)

object Sb {
    val ink = Color(0xFF24272A); val mute = Color(0xFF82827B); val line = Color(0xFFE4E1D8); val card = Color.White
    val notes = listOf(Color(0xFFF6E6A8), Color(0xFFDBE6D6), Color(0xFFD8E4EE), Color(0xFFF1D9DE), Color(0xFFE3DDF0))
    val palettes = listOf(
        listOf(Color(0xFFCFDCCB), Color(0xFFF3E7C4), Color(0xFF8FA88F)),
        listOf(Color(0xFFD8E4EE), Color.White, Color(0xFF7D9BB5)),
        listOf(Color(0xFFF3E5C9), Color(0xFFE7B45C), Color(0xFFC9965A)),
        listOf(Color(0xFFF1DDE0), Color.White, Color(0xFFB78C97)),
        listOf(Color(0xFFDCD6EA), Color(0xFFF6E6A8), Color(0xFF8A82A8)),
    )
}

val LocalSb = compositionLocalOf { Themes[0] }

@Composable fun headFont(): FontFamily = if (LocalSb.current.serif) FontFamily.Serif else FontFamily.SansSerif

@Composable
fun ScrapbookTheme(t: SbTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSb provides t) {
        MaterialTheme(colorScheme = lightColorScheme(primary = t.acc, background = t.paper, surface = t.paper, onSurface = Sb.ink), content = content)
    }
}
