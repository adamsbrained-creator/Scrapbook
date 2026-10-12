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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Every screen reads these tokens, so accent, type and corners are user-customizable. */
data class SbTheme(val name: String, val acc: Color, val tint: Color, val paper: Color,
                   val serif: Boolean = true, val radius: Dp = 14.dp)

/** The accent colors offered in Settings. The page background is not part of a theme any more: see Sb.paper and the Background setting. */
val Themes = listOf(
    SbTheme("Coral", Color(0xFFF26B4E), Color(0xFFE7E7E5), Color.White),
    SbTheme("Teal", Color(0xFF3DD9D3), Color(0xFFDDF6F5), Color.White),
    SbTheme("Amber", Color(0xFFF5B83D), Color(0xFFFBEFD2), Color.White),
    SbTheme("Violet", Color(0xFF9B8CFF), Color(0xFFE9E6FF), Color.White),
    SbTheme("Green", Color(0xFF5CCB8A), Color(0xFFDFF3E7), Color.White),
    SbTheme("Pink", Color(0xFFFF8FB1), Color(0xFFFFE7EF), Color.White),
)

object Sb {
    /** Set by ScrapbookTheme. Reading these getters in a composable subscribes it to light/dark changes. */
    var dark by mutableStateOf(false)
    /** Light mode only: true is the old "Soft grey" page, false is the default white page. Set by ScrapbookTheme. */
    var greyBg by mutableStateOf(false)
    /** The page behind everything. */
    val paper get() = if (dark) Color(0xFF0B0B0B) else if (greyBg) Color(0xFFF4F4F4) else Color.White
    val ink get() = if (dark) Color(0xFFF2F2F2) else Color(0xFF1C1C1C)
    val mute get() = if (dark) Color(0xFF9B9B9B) else Color(0xFF666666)
    val line get() = if (dark) Color(0xFF2C2C2C) else Color(0xFFE6E6E6)
    /** Cards and grouped rows sitting on the page. On the white page they are light grey, on the grey page they are white, so they always stand out. */
    val card get() = if (dark) Color(0xFF1C1C1C) else if (greyBg) Color.White else Color(0xFFF3F3F3)
    /** Sheets, floating bars and the selected piece of a segmented control. White in light mode whatever the page is. */
    val raised get() = if (dark) Color(0xFF1C1C1C) else Color.White
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

/** Per-page accent: 0 keeps the app accent, 1 to 5 pick one of these. */
val PAGE_ACCENTS = listOf(Color(0xFFF46C4E), Color(0xFF5F7F66), Color(0xFF4F6F8D), Color(0xFFA8802A), Color(0xFF8E7CF0))
fun pageAccent(i: Int): Color? = if (i <= 0) null else PAGE_ACCENTS[(i - 1) % PAGE_ACCENTS.size]

/** Page color 0 means plain. 1 to 5 tint the page with one of the pastel note colors. */
fun pageTint(color: Int): Color? = if (color <= 0) null else Sb.notes[(color - 1) % 5].copy(alpha = .4f)

val LocalSb = compositionLocalOf { Themes[0] }

/** Settings that change how lists draw, provided once in MainActivity so every list (home, boxes) follows them. */
val LocalShowPreview = compositionLocalOf { true }
/** Index into BOX_STYLES: 0 tabbed, 1 paper stack, 2 tinted tiles, 3 folder tabs, 4 chips, 5 list rows, 6 notebook covers. */
val LocalBoxStyle = compositionLocalOf { 0 }

/** Brand fonts (serif, sans). Loaded from res/font if present (see FONTS.md), otherwise system fonts. */
val LocalFonts = compositionLocalOf<Pair<FontFamily, FontFamily>> { FontFamily.Serif to FontFamily.SansSerif }

@OptIn(ExperimentalTextApi::class) // Font(variationSettings = ...) is still experimental in Compose 1.7
@Composable
fun rememberBrandFonts(): Pair<FontFamily, FontFamily> {
    val ctx = LocalContext.current
    return remember {
        fun fam(name: String, fallback: FontFamily): FontFamily {
            val id = ctx.resources.getIdentifier(name, "font", ctx.packageName)
            if (id == 0) return fallback
            return FontFamily(listOf(300, 400, 500, 600, 700).map { w ->
                Font(id, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
            })
        }
        fam("newsreader", FontFamily.Serif) to fam("outfit", fam("figtree", FontFamily.SansSerif))
    }
}

@Composable fun headFont(): FontFamily = LocalFonts.current.let { if (LocalSb.current.serif) it.first else it.second }

/** mode: 0 follows the system, 1 light, 2 dark. greyBg picks the "Soft grey" page in light mode (dark mode is always black). */
@Composable
fun ScrapbookTheme(t: SbTheme, mode: Int = 0, greyBg: Boolean = false, content: @Composable () -> Unit) {
    val dark = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    Sb.dark = dark
    Sb.greyBg = greyBg
    val eff = if (dark) t.copy(paper = Sb.paper, tint = lerp(Color(0xFF0B0B0B), t.acc, 0.25f)) else t.copy(paper = Sb.paper)
    CompositionLocalProvider(LocalSb provides eff, LocalFonts provides rememberBrandFonts()) {
        val scheme = if (dark) darkColorScheme(primary = eff.acc, background = eff.paper, surface = eff.paper, onSurface = Sb.ink, onBackground = Sb.ink)
        else lightColorScheme(primary = eff.acc, background = eff.paper, surface = eff.paper, onSurface = Sb.ink, onBackground = Sb.ink)
        MaterialTheme(colorScheme = scheme) { Surface(color = eff.paper, contentColor = Sb.ink, content = content) }
    }
}
