package app.scrapbook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every screen reads these tokens, so accent, type and corners are user-customizable. */
data class SbTheme(val name: String, val acc: Color, val tint: Color, val paper: Color,
                   val serif: Boolean = true, val radius: Dp = 14.dp)

val Themes = listOf(
    SbTheme("Scrapbook", Color(0xFFF46C4E), Color(0xFFECECEA), Color(0xFFF4F4F4)),
    SbTheme("Sage", Color(0xFF5F7F66), Color(0xFFE3EBE0), Color(0xFFF8F6F0)),
    SbTheme("Blue", Color(0xFF4F6F8D), Color(0xFFDDE7F0), Color(0xFFF4F6F8)),
    SbTheme("Butter", Color(0xFFA8802A), Color(0xFFF4E9C8), Color(0xFFFAF7EC)),
    SbTheme("Rose", Color(0xFFA0566A), Color(0xFFF2DFE3), Color(0xFFFAF5F5)),
    SbTheme("Ink", Color(0xFF24272A), Color(0xFFE6E6E3), Color(0xFFF7F7F5)),
)

object Sb {
    /** Set by ScrapbookTheme. Reading these getters in a composable subscribes it to light/dark changes. */
    var dark by mutableStateOf(false)
    val ink get() = if (dark) Color(0xFFF2F2F2) else Color(0xFF1C1C1C)
    val mute get() = if (dark) Color(0xFF9B9B9B) else Color(0xFF727272)
    val line get() = if (dark) Color(0xFF2C2C2C) else Color(0xFFE6E6E6)
    val card get() = if (dark) Color(0xFF1C1C1C) else Color.White
    private val lightNotes = listOf(Color(0xFFF6E6A8), Color(0xFFDBE6D6), Color(0xFFD8E4EE), Color(0xFFF1D9DE), Color(0xFFE3DDF0))
    private val darkNotes = listOf(Color(0xFF5A5128), Color(0xFF3E5240), Color(0xFF3A4C5E), Color(0xFF5C4048), Color(0xFF4A4560))
    val notes get() = if (dark) darkNotes else lightNotes
    val palettes = listOf(
        listOf(Color(0xFFCFDCCB), Color(0xFFF3E7C4), Color(0xFF8FA88F)),
        listOf(Color(0xFFD8E4EE), Color.White, Color(0xFF7D9BB5)),
        listOf(Color(0xFFF3E5C9), Color(0xFFE7B45C), Color(0xFFC9965A)),
        listOf(Color(0xFFF1DDE0), Color.White, Color(0xFFB78C97)),
        listOf(Color(0xFFDCD6EA), Color(0xFFF6E6A8), Color(0xFF8A82A8)),
    )
}

/** Page color 0 means plain. 1 to 5 tint the page with one of the pastel note colors. */
fun pageTint(color: Int): Color? = if (color <= 0) null else Sb.notes[(color - 1) % 5].copy(alpha = .4f)

val LocalSb = compositionLocalOf { Themes[0] }

/** Brand fonts (serif, sans). Loaded from res/font if present (see FONTS.md), otherwise system fonts. */
val LocalFonts = compositionLocalOf<Pair<FontFamily, FontFamily>> { FontFamily.Serif to FontFamily.SansSerif }

@Composable
fun rememberBrandFonts(): Pair<FontFamily, FontFamily> {
    val ctx = LocalContext.current
    return remember {
        fun fam(name: String, fallback: FontFamily): FontFamily =
            ctx.resources.getIdentifier(name, "font", ctx.packageName).let { if (it == 0) fallback else FontFamily(Font(it)) }
        fam("newsreader", FontFamily.Serif) to fam("outfit", fam("figtree", FontFamily.SansSerif))
    }
}

@Composable fun headFont(): FontFamily = LocalFonts.current.let { if (LocalSb.current.serif) it.first else it.second }

/** mode: 0 follows the system, 1 light, 2 dark. */
@Composable
fun ScrapbookTheme(t: SbTheme, mode: Int = 0, content: @Composable () -> Unit) {
    val dark = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    Sb.dark = dark
    val eff = if (dark) t.copy(paper = Color(0xFF0B0B0B), tint = lerp(Color(0xFF0B0B0B), t.acc, 0.25f)) else t
    CompositionLocalProvider(LocalSb provides eff, LocalFonts provides rememberBrandFonts()) {
        val scheme = if (dark) darkColorScheme(primary = eff.acc, background = eff.paper, surface = eff.paper, onSurface = Sb.ink, onBackground = Sb.ink)
        else lightColorScheme(primary = eff.acc, background = eff.paper, surface = eff.paper, onSurface = Sb.ink, onBackground = Sb.ink)
        MaterialTheme(colorScheme = scheme) { Surface(color = eff.paper, contentColor = Sb.ink, content = content) }
    }
}
