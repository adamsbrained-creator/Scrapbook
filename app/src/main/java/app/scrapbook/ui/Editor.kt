package app.scrapbook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun Field(v: String, onChange: (String) -> Unit, hint: String, size: TextUnit, font: FontFamily, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    BasicTextField(v, onChange, Modifier.fillMaxWidth().then(modifier),
        textStyle = TextStyle(fontSize = size, fontFamily = font, fontWeight = weight, color = Sb.ink, lineHeight = size * 1.4f),
        cursorBrush = SolidColor(LocalSb.current.acc),
        decorationBox = { inner -> Box { if (v.isEmpty()) Text(hint, color = Sb.mute, fontSize = size, fontFamily = font, fontWeight = weight); inner() } })
}

/** What the editor shows for the raw text: the marks hidden, plus the maps between shown and saved positions. */
private class MdView(val source: String, val out: TransformedText, val o2t: IntArray, private val lo: IntArray, private val hi: IntArray) {
    /** Saved offset for the start of a range: after any hidden opening marks. */
    fun start(t: Int) = hi[t.coerceIn(0, hi.lastIndex)]
    /** Saved offset for the end of a range: before any hidden closing marks. */
    fun end(t: Int) = lo[t.coerceIn(0, lo.lastIndex)]
}

private const val DIVIDER_GLYPHS = "────────────────"

/**
 * Styles the text as you type and hides the markdown marks. Line prefixes (# , ## , > ) are always hidden, bullets and
 * checkboxes are drawn as • ☐ ☑, and inline marks (** ~~ == * and the [ ](url) of a link) disappear until the cursor
 * touches that piece of text. The saved text is never changed; the returned maps translate between saved and shown positions.
 */
private fun mdRender(text: String, sel: TextRange?, acc: Color, baseLine: Float): MdView {
    val n = text.length
    val styled = buildAnnotatedString {
        append(text); var i = 0
        text.split("\n").forEach { line ->
            val st: SpanStyle? = when {
                line.startsWith("## ") -> SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                line.startsWith("# ") -> SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                line.startsWith("> ") -> SpanStyle(fontStyle = FontStyle.Italic, color = Sb.mute)
                line.startsWith("- [x] ") -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = Sb.mute)
                line == "---" -> SpanStyle(color = Sb.mute)
                else -> null
            }
            if (st != null && line.isNotEmpty()) addStyle(st, i, i + line.length)
            if (line.startsWith("- [ ] ") || line.startsWith("- [x] ")) addStyle(SpanStyle(color = acc, fontWeight = FontWeight.Bold), i, i + 5)
            else if (line.startsWith("- ")) addStyle(SpanStyle(color = acc, fontWeight = FontWeight.Bold), i, i + 1)
            // Numbered lists keep the normal text color on purpose.
            styleInline(line, i, acc); styleLinks(line, i, acc); styleSpans(line, i)
            // Alignment, indent and the taller lines big text needs are paragraph styles; each covers its line and the line break after it.
            val (_, lineTags) = leadOf(line); val (ind, al) = tagsOf(lineTags)
            val big = SPAN_RE.findAll(line).maxOfOrNull { parseSpanAttrs(it.groupValues[1]).size ?: 0 } ?: 0
            val tall = big * 1.3f > baseLine
            if (ind > 0 || al != 'l' || tall) addStyle(ParagraphStyle(
                textAlign = when (al) { 'c' -> TextAlign.Center; 'r' -> TextAlign.End; else -> TextAlign.Unspecified },
                lineHeight = if (tall) (big * 1.3f).sp else TextUnit.Unspecified,
                textIndent = if (ind > 0) TextIndent((24 * ind).sp, (24 * ind).sp) else null), i, minOf(i + line.length + 1, n))
            i += line.length + 1
        }
    }

    // Which saved characters are hidden or swapped for a glyph.
    val hide = BooleanArray(n); val closing = BooleanArray(n); val repl = arrayOfNulls<Pair<Int, String>>(n)
    val selA = sel?.let { minOf(it.start, it.end) } ?: 0; val selB = sel?.let { maxOf(it.start, it.end) } ?: 0
    // Marks come back only while the cursor sits in or next to them; a selection keeps them hidden, so formatting shows at once.
    fun touches(from: Int, to: Int) = sel != null && sel.collapsed && selA <= to && selB >= from
    fun hideRun(from: Int, to: Int, isClosing: Boolean) { for (k in from until minOf(to, n)) { hide[k] = true; closing[k] = isClosing } }
    fun inline(s: String, off: Int) {
        for (m in INLINE.findAll(s)) {
            val g = m.groupValues; val s0 = off + m.range.first; val e0 = off + m.range.last + 1
            val mk = if (g[4].isNotEmpty()) 1 else 2; val inner = innerOf(g)
            // "2 * 3 * 4" is not italic: a single * next to a space stays visible.
            val spaced = g[4].isNotEmpty() && (inner.first().isWhitespace() || inner.last().isWhitespace())
            if (!spaced && !touches(s0, e0)) { hideRun(s0, s0 + mk, false); hideRun(e0 - mk, e0, true) }
            inline(inner, s0 + mk)
        }
    }
    var i = 0
    for (line in text.split("\n")) {
        val le = i + line.length
        val (pfx, tags) = leadOf(line); val hasText = line.length > pfx.length + tags.length
        when {
            line.startsWith("## ") || line.startsWith("# ") || line.startsWith("> ") -> if (hasText) hideRun(i, i + pfx.length, false)
            line.startsWith("- [ ] ") -> repl[i] = 5 to "☐"
            line.startsWith("- [x] ") -> repl[i] = 5 to "☑"
            line.startsWith("- ") -> repl[i] = 1 to "•"
            line == "---" && !touches(i, le) -> repl[i] = 3 to DIVIDER_GLYPHS
        }
        if (tags.isNotEmpty()) hideRun(i + pfx.length, i + pfx.length + tags.length, false)
        inline(line, i)
        for (m in SPAN_RE.findAll(line)) {
            val s0 = i + m.range.first; val e0 = i + m.range.last + 1; val cr = m.groups[2]!!.range
            if (!touches(s0, e0)) { hideRun(s0, i + cr.first, false); hideRun(i + cr.last + 1, e0, true) }
        }
        for (m in LINK.findAll(line)) {
            val s0 = i + m.range.first; val e0 = i + m.range.last + 1
            if (!touches(s0, e0)) { hideRun(s0, s0 + 1, false); hideRun(s0 + 1 + m.groupValues[1].length, e0, true) }
        }
        i = le + 1
    }

    // Shown text, and where every saved offset lands in it.
    val shown = StringBuilder(); val o2t = IntArray(n + 1)
    var k = 0
    while (k < n) {
        val r = repl[k]
        if (r != null) {
            o2t[k] = shown.length; shown.append(r.second)
            for (j in 1 until r.first) o2t[k + j] = shown.length
            k += r.first
        } else {
            o2t[k] = shown.length
            if (!hide[k]) shown.append(text[k])
            k++
        }
    }
    o2t[n] = shown.length
    val m = shown.length
    // Back again: several saved offsets can share one shown offset (next to hidden marks). Opening marks put the cursor
    // after them (inside the formatting), closing marks before them.
    val lo = IntArray(m + 1) { Int.MAX_VALUE }; val hi = IntArray(m + 1) { -1 }; val allOpen = BooleanArray(m + 1) { true }
    for (j in 0..n) { val t = o2t[j]; if (j < lo[t]) lo[t] = j; if (j > hi[t]) hi[t] = j }
    for (j in 0 until n) if (hide[j] && closing[j]) allOpen[o2t[j]] = false
    for (t in 1..m) if (hi[t] < 0) { lo[t] = lo[t - 1]; hi[t] = hi[t - 1] }
    val pick = IntArray(m + 1) { if (allOpen[it]) hi[it] else lo[it] }

    val shownText = buildAnnotatedString {
        append(shown.toString())
        for (r in styled.spanStyles) {
            val s = o2t[r.start.coerceIn(0, n)]; val e = o2t[r.end.coerceIn(0, n)]
            if (e > s) addStyle(r.item, s, e)
        }
        for (r in styled.paragraphStyles) {
            val s = o2t[r.start.coerceIn(0, n)]; val e = o2t[r.end.coerceIn(0, n)]
            if (e > s) addStyle(r.item, s, e)
        }
    }
    val map = object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, n)]
        override fun transformedToOriginal(offset: Int) = pick[offset.coerceIn(0, m)]
    }
    return MdView(text, TransformedText(shownText, map), o2t, lo, hi)
}

private val CHECK_LINE = Regex("(?m)^- \\[[ x]\\] ")

/** The saved offset of the line start whose ☐/☑ is under the finger, or null. */
private fun checkboxAt(lay: TextLayoutResult, v: MdView, text: String, p: Offset, pad: Float): Int? {
    for (m in CHECK_LINE.findAll(text)) {
        val t = v.o2t[m.range.first.coerceIn(0, v.o2t.lastIndex)]
        if (t >= lay.layoutInput.text.length) continue
        val box = lay.getBoundingBox(t)
        if (p.x >= box.left - pad && p.x <= box.right + pad && p.y >= box.top && p.y <= box.bottom) return m.range.first
    }
    return null
}

private val PASTED_URL = Regex("^https?://\\S+$")

/**
 * Smart edits on top of plain typing: pasting a web address over selected text makes it a link, and backspace at the
 * start of a list, heading or quote removes the whole (hidden) mark in one go.
 */
private fun smartEdit(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    val os = old.selection; val a = minOf(os.start, os.end); val b = maxOf(os.start, os.end)
    if (a != b && new.selection.collapsed) {
        val ins = new.text.length - (old.text.length - (b - a))
        if (ins > 0 && new.selection.start == a + ins && new.text.startsWith(old.text.substring(0, a)) && new.text.endsWith(old.text.substring(b))) {
            val pasted = new.text.substring(a, a + ins); val label = old.text.substring(a, b)
            if (PASTED_URL.matches(pasted) && label.none { it == '\n' || it == '[' || it == ']' })
                return new.copy(text = old.text.substring(0, a) + "[$label]($pasted)" + old.text.substring(b), selection = TextRange(a + 1, a + 1 + label.length))
        }
    }
    if (os.collapsed && new.text.length == old.text.length - 1 && new.selection.collapsed && new.selection.start == os.start - 1) {
        val s = old.text; val pos = os.start
        val ls = s.lastIndexOf('\n', pos - 1) + 1; val le = s.indexOf('\n', ls).let { if (it < 0) s.length else it }
        val line = s.substring(ls, le)
        val hidden = LINE_PREFIXES.firstOrNull { line.startsWith(it) }.orEmpty()
        val numPfx = if (hidden.isEmpty()) NUM.find(line)?.value.orEmpty() else ""
        val tags = LINE_TAGS.find(line.substring(hidden.length + numPfx.length))?.value.orEmpty()
        if (hidden.length + numPfx.length + tags.length > 0 && pos == ls + hidden.length + numPfx.length + tags.length) {
            // The mark in front of the text is hidden, so one backspace removes all of it: the prefix first, then the layout tags.
            val from = if (hidden.isNotEmpty()) ls else ls + numPfx.length
            val to = if (hidden.isNotEmpty()) ls + hidden.length else if (tags.isNotEmpty()) from + tags.length else from
            if (to > from) return new.copy(text = s.removeRange(from, to), selection = TextRange(pos - (to - from)))
        }
    }
    return new
}

private val NUM = Regex("^(\\d+)\\. ")
private val INLINE = Regex("\\*\\*(.+?)\\*\\*|~~(.+?)~~|==(.+?)==|\\*(.+?)\\*")
private val LINK = Regex("\\[([^\\]\\n]+)\\]\\(([^)\\s]*)\\)")

/** Which marker an INLINE match used: "**", "~~", "==" or "*". */
private fun markerOf(g: List<String>) = when { g[1].isNotEmpty() -> "**"; g[2].isNotEmpty() -> "~~"; g[3].isNotEmpty() -> "=="; else -> "*" }
private fun innerOf(g: List<String>) = g[1].ifEmpty { g[2] }.ifEmpty { g[3] }.ifEmpty { g[4] }

/** Styles bold, strike, highlight and italic runs (also when nested) and dims their markers. */
private fun AnnotatedString.Builder.styleInline(s: String, off: Int, acc: Color) {
    for (m in INLINE.findAll(s)) {
        val g = m.groupValues; val s0 = off + m.range.first; val e0 = off + m.range.last + 1
        val mk = if (g[4].isNotEmpty()) 1 else 2
        val st2 = when {
            g[1].isNotEmpty() -> SpanStyle(fontWeight = FontWeight.Bold)
            g[2].isNotEmpty() -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            g[3].isNotEmpty() -> SpanStyle(background = acc.copy(alpha = .3f))
            else -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        addStyle(st2, s0, e0); addStyle(SpanStyle(color = Sb.mute), s0, s0 + mk); addStyle(SpanStyle(color = Sb.mute), e0 - mk, e0)
        styleInline(innerOf(g), s0 + mk, acc)
    }
}

/** {c:ff0000 s:20 u|text}: color, size and underline for the text, the braces and attributes dimmed (they are hidden unless the cursor is in them). */
private fun AnnotatedString.Builder.styleSpans(s: String, off: Int) {
    for (m in SPAN_RE.findAll(s)) {
        val a = parseSpanAttrs(m.groupValues[1]); val s0 = off + m.range.first; val e0 = off + m.range.last + 1
        val cr = m.groups[2]!!.range; val cs = off + cr.first; val ce = off + cr.last + 1
        addStyle(SpanStyle(color = a.color?.let { hexColor(it) } ?: Color.Unspecified, fontSize = a.size?.sp ?: TextUnit.Unspecified,
            textDecoration = if (a.underline) TextDecoration.Underline else null), cs, ce)
        addStyle(SpanStyle(color = Sb.mute), s0, cs); addStyle(SpanStyle(color = Sb.mute), ce, e0)
    }
}

/** [text](url): the text looks like a link, the brackets and address are dimmed. */
private fun AnnotatedString.Builder.styleLinks(s: String, off: Int, acc: Color) {
    for (m in LINK.findAll(s)) {
        val s0 = off + m.range.first; val e0 = off + m.range.last + 1; val len = m.groupValues[1].length
        addStyle(SpanStyle(color = Sb.mute), s0, s0 + 1)
        addStyle(SpanStyle(color = acc, textDecoration = TextDecoration.Underline), s0 + 1, s0 + 1 + len)
        addStyle(SpanStyle(color = Sb.mute), s0 + 1 + len, e0)
    }
}

private val LINE_PREFIXES = listOf("## ", "# ", "> ", "- [x] ", "- [ ] ", "- ")

/** Adds, switches or removes a line prefix (heading, quote, bullet, checklist) on the cursor's line. */
private fun TextFieldValue.toggleLine(prefix: String): TextFieldValue {
    val s = text; val pos = selection.start.coerceIn(0, s.length)
    val ls = s.lastIndexOf('\n', pos - 1) + 1
    val le = s.indexOf('\n', pos).let { if (it < 0) s.length else it }
    val line = s.substring(ls, le)
    val num = NUM.find(line)?.value
    val cur = LINE_PREFIXES.firstOrNull { line.startsWith(it) } ?: num
    val stripped = if (cur != null) line.removePrefix(cur) else line
    val same = cur == prefix || (prefix == "1. " && num != null)
    val newLine = if (same) stripped else prefix + stripped
    val d = newLine.length - line.length
    return copy(text = s.substring(0, ls) + newLine + s.substring(le),
        selection = TextRange((selection.start + d).coerceAtLeast(ls), (selection.end + d).coerceAtLeast(ls)))
}

private fun TextFieldValue.wrap(marker: String): TextFieldValue {
    val a = minOf(selection.start, selection.end).coerceIn(0, text.length); val b = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    val m = marker.length; val sel = text.substring(a, b)
    val partOfBold = m == 1 && ((a >= 2 && text[a - 2] == '*') || (b + 2 <= text.length && text[b + 1] == '*'))
    if (!partOfBold && a >= m && b + m <= text.length && text.substring(a - m, a) == marker && text.substring(b, b + m) == marker)
        return copy(text = text.substring(0, a - m) + sel + text.substring(b + m), selection = TextRange(a - m, b - m))
    return copy(text = text.substring(0, a) + marker + sel + marker + text.substring(b), selection = TextRange(a + m, b + m))
}

private fun TextFieldValue.insertDivider(): TextFieldValue {
    val pos = selection.start.coerceIn(0, text.length); val before = text.substring(0, pos)
    val ins = (if (before.isEmpty() || before.endsWith("\n")) "" else "\n") + "---\n"
    return copy(text = before + ins + text.substring(pos), selection = TextRange(pos + ins.length))
}

private fun marks(s: String, off: Int, out: MutableList<Pair<String, IntRange>>) {
    for (r in INLINE.findAll(s)) {
        val g = r.groupValues; val mk = markerOf(g); val from = off + r.range.first
        out += mk to (from until off + r.range.last + 1)
        marks(innerOf(g), from + mk.length, out)
    }
}

/** The pair of this marker ("**", "~~", "==" or "*") that surrounds the selection, as a range of the whole text. */
private fun TextFieldValue.enclosing(marker: String): IntRange? {
    val s = text; val a = minOf(selection.start, selection.end).coerceIn(0, s.length); val b = maxOf(selection.start, selection.end).coerceIn(0, s.length)
    val ls = s.lastIndexOf('\n', a - 1) + 1; val le = s.indexOf('\n', a).let { if (it < 0) s.length else it }
    val found = mutableListOf<Pair<String, IntRange>>(); marks(s.substring(ls, le), ls, found)
    val m = marker.length
    return found.firstOrNull { it.first == marker && a >= it.second.first + m && b <= it.second.last + 1 - m }?.second
}

/** Bold, italic, strike or highlight: removes the pair around the selection if there is one, otherwise wraps the selection. */
private fun TextFieldValue.toggleInline(marker: String): TextFieldValue {
    val r = enclosing(marker) ?: return wrap(marker)
    val m = marker.length; val a = minOf(selection.start, selection.end); val b = maxOf(selection.start, selection.end)
    return copy(text = text.substring(0, r.first) + text.substring(r.first + m, r.last + 1 - m) + text.substring(r.last + 1),
        selection = TextRange(a - m, b - m))
}

/** The line prefix on the cursor's line: "# ", "## ", "> ", "- ", "- [ ] ", "- [x] ", "1. " for any number, or null. */
private fun TextFieldValue.linePrefix(): String? {
    val s = text; val pos = selection.start.coerceIn(0, s.length)
    val ls = s.lastIndexOf('\n', pos - 1) + 1; val le = s.indexOf('\n', pos).let { if (it < 0) s.length else it }
    val line = s.substring(ls, le)
    return LINE_PREFIXES.firstOrNull { line.startsWith(it) } ?: NUM.find(line)?.let { "1. " }
}

/** One heading button: none, then heading, then subheading, then none again. */
private fun TextFieldValue.cycleHeading(): TextFieldValue = when (linePrefix()) { "# " -> toggleLine("## "); "## " -> toggleLine("## "); else -> toggleLine("# ") }

/** The [text](url) link around the selection, with its range in the whole text. */
private fun TextFieldValue.link(): Pair<IntRange, MatchResult>? {
    val s = text; val a = minOf(selection.start, selection.end).coerceIn(0, s.length); val b = maxOf(selection.start, selection.end).coerceIn(0, s.length)
    val ls = s.lastIndexOf('\n', a - 1) + 1; val le = s.indexOf('\n', a).let { if (it < 0) s.length else it }
    for (m in LINK.findAll(s.substring(ls, le))) {
        val from = ls + m.range.first; val to = ls + m.range.last + 1
        if (a >= from && b <= to) return (from until to) to m
    }
    return null
}

/** Turns the selected text into a link to url, or changes the address if the selection is already a link. */
private fun TextFieldValue.setLink(url: String): TextFieldValue {
    val found = link()
    if (found != null) {
        val (r, m) = found; val label = m.groupValues[1]
        return copy(text = text.substring(0, r.first) + "[$label]($url)" + text.substring(r.last + 1), selection = TextRange(r.first + 1, r.first + 1 + label.length))
    }
    val a = minOf(selection.start, selection.end).coerceIn(0, text.length); val b = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    val label = text.substring(a, b).ifEmpty { url }
    if (label.any { it == '\n' || it == '[' || it == ']' }) return this
    return copy(text = text.substring(0, a) + "[$label]($url)" + text.substring(b), selection = TextRange(a + 1, a + 1 + label.length))
}

private fun TextFieldValue.removeLink(): TextFieldValue {
    val (r, m) = link() ?: return this
    val label = m.groupValues[1]
    return copy(text = text.substring(0, r.first) + label + text.substring(r.last + 1), selection = TextRange(r.first, r.first + label.length))
}

/** A saved color such as "ff3b30" as a Compose color. */
fun hexColor(hex: String): Color = Color(0xFF000000L or (hex.toLongOrNull(16) ?: 0L))

/** The prefix (# , - , 1.  and so on) and the layout tags at the start of a line. */
private fun leadOf(line: String): Pair<String, String> {
    val prefix = LINE_PREFIXES.firstOrNull { line.startsWith(it) } ?: NUM.find(line)?.value ?: ""
    return prefix to (LINE_TAGS.find(line.substring(prefix.length))?.value ?: "")
}

private fun tagString(indent: Int, align: Char) = (if (indent > 0) "{i:$indent}" else "") + (if (align == 'c' || align == 'r') "{a:$align}" else "")

/** Indent level and alignment ('l', 'c' or 'r') written in a line's tags. */
private fun tagsOf(tags: String): Pair<Int, Char> {
    val m = LINE_TAGS.find(tags); return (m?.groupValues?.get(1)?.toIntOrNull() ?: 0) to (m?.groupValues?.get(2)?.firstOrNull() ?: 'l')
}

/** Indent and alignment of the line the selection starts on. */
private fun TextFieldValue.lineState(): Pair<Int, Char> {
    val s = text; val pos = selection.start.coerceIn(0, s.length)
    val ls = s.lastIndexOf('\n', pos - 1) + 1; val le = s.indexOf('\n', pos).let { if (it < 0) s.length else it }
    return tagsOf(leadOf(s.substring(ls, le)).second)
}

/** Changes the indent and alignment of every line the selection touches. change gets (indent, align) and returns the new pair. */
private fun TextFieldValue.changeLines(change: (Int, Char) -> Pair<Int, Char>): TextFieldValue {
    val s = text; val a = minOf(selection.start, selection.end).coerceIn(0, s.length); val b = maxOf(selection.start, selection.end).coerceIn(0, s.length)
    val lines = s.split("\n"); val out = StringBuilder(); val mods = mutableListOf<Pair<Int, Int>>() // (end of old tags, size change)
    var i = 0
    for ((idx, line) in lines.withIndex()) {
        val le = i + line.length
        if (le >= a && i <= b) {
            val (pfx, tags) = leadOf(line); val (ind, al) = tagsOf(tags)
            val (ni, na) = change(ind, al); val nt = tagString(ni.coerceIn(0, 4), na)
            if (nt != tags) {
                out.append(pfx).append(nt).append(line.substring(pfx.length + tags.length)); mods += (i + pfx.length + tags.length) to (nt.length - tags.length)
            } else out.append(line)
        } else out.append(line)
        if (idx < lines.size - 1) out.append('\n')
        i = le + 1
    }
    if (mods.isEmpty()) return this
    fun shift(o: Int) = o + mods.filter { o >= it.first }.sumOf { it.second }
    return copy(text = out.toString(), selection = TextRange(shift(selection.start), shift(selection.end)))
}

private fun TextFieldValue.setAlign(c: Char) = changeLines { i, _ -> i to c }
private fun TextFieldValue.indentBy(d: Int) = changeLines { i, a -> (i + d) to a }

/** A run of a line: plain text or one {..|..} span. from..to is the whole run, cs..ce the text inside it. */
private class Atom(val from: Int, val to: Int, val cs: Int, val ce: Int, val attrs: SpanAttrs)

private fun atoms(line: String): List<Atom> {
    val out = mutableListOf<Atom>(); var p = 0
    for (m in SPAN_RE.findAll(line)) {
        if (m.range.first > p) out += Atom(p, m.range.first, p, m.range.first, SpanAttrs())
        val c = m.groups[2]!!.range
        out += Atom(m.range.first, m.range.last + 1, c.first, c.last + 1, parseSpanAttrs(m.groupValues[1])); p = m.range.last + 1
    }
    if (p < line.length) out += Atom(p, line.length, p, line.length, SpanAttrs())
    return out
}

private fun wrapSpan(text: String, a: SpanAttrs) = if (text.isEmpty() || a.none) text else "{${a.tag()}|$text}"

/** Rewrites one line so that f is applied to the formatting of the text in x until y. Returns the new line and the new x and y, or null if nothing changed. */
private fun rewriteLine(line: String, x: Int, y: Int, f: (SpanAttrs) -> SpanAttrs): Triple<String, Int, Int>? {
    val sb = StringBuilder(); var nx = -1; var ny = -1
    for (at in atoms(line)) {
        val lo = maxOf(at.cs, x); val hi = minOf(at.ce, y)
        if (lo >= hi) { sb.append(line, at.from, at.to); continue }
        val mid = line.substring(lo, hi)
        if ('}' in mid) return null
        val na = f(at.attrs); val head = if (na.none) 0 else na.tag().length + 2
        sb.append(wrapSpan(line.substring(at.cs, lo), at.attrs))
        if (nx < 0) nx = sb.length + head
        ny = sb.length + head + mid.length
        sb.append(wrapSpan(mid, na)).append(wrapSpan(line.substring(hi, at.ce), at.attrs))
    }
    return if (nx < 0) null else Triple(sb.toString(), nx, ny)
}

/** Colors, resizes or underlines the text from a to b by changing its {..|..} spans. Line prefixes are never wrapped. The result selects the same text. */
private fun TextFieldValue.formatSpan(a: Int, b: Int, f: (SpanAttrs) -> SpanAttrs): TextFieldValue {
    val lines = text.split("\n"); val out = StringBuilder(); var ns = -1; var ne = -1; var i = 0
    for ((idx, line) in lines.withIndex()) {
        val le = i + line.length; val (pfx, tags) = leadOf(line)
        val x = maxOf(maxOf(a, i) - i, pfx.length + tags.length); val y = minOf(b, le) - i
        val r = if (y > x) rewriteLine(line, x, y, f) else null
        if (r != null) { if (ns < 0) ns = out.length + r.second; ne = out.length + r.third; out.append(r.first) } else out.append(line)
        if (idx < lines.size - 1) out.append('\n')
        i = le + 1
    }
    return if (ns < 0) this else copy(text = out.toString(), selection = TextRange(ns, ne))
}

/** The formatting of every run of text between a and b (plain text counts as no formatting). */
private fun TextFieldValue.attrsIn(a: Int, b: Int): List<SpanAttrs> {
    val res = mutableListOf<SpanAttrs>(); var i = 0
    for (line in text.split("\n")) {
        val le = i + line.length; val (pfx, tags) = leadOf(line)
        val x = maxOf(maxOf(a, i) - i, pfx.length + tags.length); val y = minOf(b, le) - i
        if (y > x) for (at in atoms(line)) if (maxOf(at.cs, x) < minOf(at.ce, y)) res += at.attrs
        i = le + 1
    }
    return res
}

/** Enter inside a list continues it; Enter on an empty item leaves the list. A new line keeps the indent and alignment of the one above. */
private fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.text.length != old.text.length + 1 || new.selection.start == 0 || new.text[new.selection.start - 1] != '\n') return new
    val s = new.text; val nl = new.selection.start - 1
    val ls = s.lastIndexOf('\n', nl - 1) + 1; val line = s.substring(ls, nl)
    val numM = NUM.find(line)
    val listPfx = numM?.value ?: listOf("- [x] ", "- [ ] ", "- ").firstOrNull { line.startsWith(it) }
    val (anyPfx, tags) = leadOf(line)
    if (listPfx == null && tags.isEmpty()) return new
    if (line.length == anyPfx.length + tags.length) return new.copy(text = s.substring(0, ls) + s.substring(nl), selection = TextRange(ls + 1))
    val cont = when { numM != null -> "${numM.groupValues[1].toInt() + 1}. "; listPfx == "- [x] " -> "- [ ] "; listPfx != null -> listPfx; else -> "" } + tags
    return new.copy(text = s.substring(0, nl + 1) + cont + s.substring(nl + 1), selection = TextRange(nl + 1 + cont.length))
}

private val BUBBLE_W = 248.dp
private val BUBBLE_H = 48.dp

@Composable
private fun BubbleBtn(on: Boolean, desc: String, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Sb.card.copy(alpha = .22f) else Color.Transparent)
        .alpha(if (enabled) 1f else .35f).semantics { selected = on }.tap(desc) { if (enabled) onClick() }, Alignment.Center) { content() }
}

/** The small dark bubble under the selected text: bold, italic, strikethrough, highlight, link and copy. It flips above the text near the bottom edge. */
@Composable
private fun SelectionBubble(value: TextFieldValue, layout: TextLayoutResult, map: OffsetMapping, fieldTop: Float, scrollY: Int, viewportH: Int, onInline: (String) -> Unit, onLink: () -> Unit, onCopy: () -> Unit) {
    val t = LocalSb.current; val d = LocalDensity.current
    val n = value.text.length; val tn = layout.layoutInput.text.length
    val a = minOf(value.selection.start, value.selection.end).coerceIn(0, n); val b = maxOf(value.selection.start, value.selection.end).coerceIn(0, n)
    if (a == b) return
    // The layout is of the shown text (marks hidden), so saved offsets are translated first.
    val ta = map.originalToTransformed(a).coerceIn(0, tn); val tb = map.originalToTransformed(b).coerceIn(0, tn)
    val ra = layout.getCursorRect(ta); val rb = layout.getCursorRect(tb)
    val sameLine = layout.getLineForOffset(ta) == layout.getLineForOffset(tb)
    val w = with(d) { BUBBLE_W.toPx() }; val h = with(d) { BUBBLE_H.toPx() }
    val centerX = if (sameLine) (ra.left + rb.left) / 2f else layout.size.width / 2f
    val left = (centerX - w / 2f).coerceIn(0f, maxOf(0f, layout.size.width - w))
    val below = rb.bottom + with(d) { 30.dp.toPx() }; val above = ra.top - with(d) { 14.dp.toPx() } - h
    val flip = viewportH > 0 && fieldTop + below + h > scrollY + viewportH && fieldTop + above >= scrollY
    val top = if (flip) above else below
    val edge = with(d) { 18.dp.toPx() }; val arrowX = (centerX - left).coerceIn(edge, w - edge)
    val mark = lerp(t.acc, Color.White, .5f)
    val canLink = value.link() != null || !value.text.substring(a, b).any { it == '\n' || it == '[' || it == ']' }
    Box(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(BUBBLE_W, BUBBLE_H)) {
        Box(Modifier.offset { IntOffset((arrowX - 5.dp.toPx()).roundToInt(), (if (flip) h - 5.dp.toPx() else -5.dp.toPx()).roundToInt()) }.size(10.dp).rotate(45f).background(Sb.ink))
        Row(Modifier.fillMaxSize().shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Sb.ink).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            BubbleBtn(value.enclosing("**") != null, "Bold", onClick = { onInline("**") }) { Text("B", color = Sb.card, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            BubbleBtn(value.enclosing("*") != null, "Italic", onClick = { onInline("*") }) { Text("I", color = Sb.card, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic) }
            BubbleBtn(value.enclosing("~~") != null, "Strikethrough", onClick = { onInline("~~") }) { Text("S", color = Sb.card, fontSize = 17.sp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.LineThrough) }
            BubbleBtn(value.enclosing("==") != null, "Highlight", onClick = { onInline("==") }) {
                Text("ab", color = Color(0xFF161616), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(mark).padding(horizontal = 5.dp, vertical = 1.dp))
            }
            BubbleBtn(value.link() != null, "Add link", enabled = canLink, onClick = onLink) { Ic("link", 20.dp, Sb.card) }
            BubbleBtn(false, "Copy", onClick = onCopy) { Ic("copy", 20.dp, Sb.card) }
        }
    }
}

/** The rounded 56dp bar the editor toolbars sit in. floating adds a border and shadow (the pill). */
@Composable
private fun Bar(floating: Boolean, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
        .then(if (floating) Modifier.shadow(10.dp, shape) else Modifier)
        .height(56.dp).clip(shape).background(Sb.card)
        .then(if (floating) Modifier.border(1.dp, Sb.line, shape) else Modifier)
        .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

/** One button in a bar. on tints it with the accent; hold runs onHold (for example redo on the undo button). */
@Composable
private fun RowScope.BarCell(on: Boolean, desc: String, holdLabel: String? = null, onHold: (() -> Unit)? = null, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val acc = LocalSb.current.acc
    val base = Modifier.weight(1f).height(44.dp).padding(horizontal = 1.dp).clip(RoundedCornerShape(16.dp))
        .background(if (on) acc.copy(alpha = .16f) else Color.Transparent).semantics { selected = on }
    Box(if (onHold != null) base.tapOrHold(desc, holdLabel, onHold, onClick) else base.tap(desc, onClick), Alignment.Center) { content(if (on) acc else Sb.ink) }
}

private fun headingLabel(prefix: String?) = when (prefix) { "# " -> "H1"; "## " -> "H2"; else -> "H" }

/** Editor A, the default: a round + button (quote, divider), heading, checklist, text options, font color, font size and undo. Inline formatting is also in the selection bubble. */
@Composable
private fun BubbleBar(prefix: String?, canUndo: Boolean, canRedo: Boolean, sizeLabel: String, colorBar: Color, panel: Int, onPanel: (Int) -> Unit,
                      onQuote: () -> Unit, onDivider: () -> Unit, onHeading: () -> Unit, onCheck: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit) {
    Bar(floating = false) {
        var menu by remember { mutableStateOf(false) }
        Box {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Sb.ink).tap("Insert") { menu = true }, Alignment.Center) { Ic("plus", 22.dp, Sb.card) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Quote") }, onClick = { menu = false; onQuote() })
                DropdownMenuItem(text = { Text("Divider") }, onClick = { menu = false; onDivider() })
            }
        }
        BarCell(prefix == "# " || prefix == "## ", "Heading", onClick = onHeading) { c -> Text(headingLabel(prefix), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c) }
        BarCell(prefix == "- [ ] " || prefix == "- [x] ", "Checklist", onClick = onCheck) { c -> Ic("check", 22.dp, c) }
        BarCell(panel == 1, "Text options", onClick = { onPanel(if (panel == 1) 0 else 1) }) { c -> Text("Aa", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c) }
        BarCell(panel == 2, "Font color", onClick = { onPanel(if (panel == 2) 0 else 2) }) { c ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("A", fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = c)
                Box(Modifier.padding(top = 2.dp).width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colorBar))
            }
        }
        BarCell(panel == 3, "Font size", onClick = { onPanel(if (panel == 3) 0 else 3) }) { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sizeLabel, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c)
                Text("▾", fontSize = 11.sp, color = c)
            }
        }
        BarCell(false, "Undo", "Redo", onRedo, onUndo) { c -> Ic("undo", 22.dp, if (canUndo) c else if (canRedo) Sb.mute else Sb.mute.copy(alpha = .4f)) }
    }
}

/** Editor B: one floating pill with inline formatting and block buttons that light up for the text under the cursor. */
@Composable
private fun PillBar(value: TextFieldValue, onInline: (String) -> Unit, onHeading: () -> Unit, onBullet: () -> Unit, onNumber: () -> Unit, onCheck: () -> Unit) {
    val t = LocalSb.current; val prefix = value.linePrefix(); val mark = lerp(t.acc, Color.White, .5f)
    Bar(floating = true) {
        BarCell(value.enclosing("**") != null, "Bold", onClick = { onInline("**") }) { c -> Text("B", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c) }
        BarCell(value.enclosing("*") != null, "Italic", onClick = { onInline("*") }) { c -> Text("I", fontSize = 17.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, color = c) }
        BarCell(value.enclosing("~~") != null, "Strikethrough", onClick = { onInline("~~") }) { c -> Text("S", fontSize = 17.sp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.LineThrough, color = c) }
        BarCell(value.enclosing("==") != null, "Highlight", onClick = { onInline("==") }) { _ ->
            Text("ab", color = Color(0xFF161616), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(mark).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(24.dp).background(Sb.line))
        BarCell(prefix == "# " || prefix == "## ", "Heading", onClick = onHeading) { c -> Text(headingLabel(prefix), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c) }
        BarCell(prefix == "- ", "Bullet list", onClick = onBullet) { c -> Ic("list", 22.dp, c) }
        BarCell(prefix == "1. ", "Numbered list", onClick = onNumber) { c -> Ic("numbered", 22.dp, c) }
        BarCell(prefix == "- [ ] " || prefix == "- [x] ", "Checklist", onClick = onCheck) { c -> Ic("check", 22.dp, c) }
    }
}

/** A photo as a taped polaroid; the cross removes it. */
@Composable
private fun Polaroid(uri: String, kind: Kind, i: Int, onRemove: () -> Unit) {
    Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp).rotate(if (i % 2 == 0) -3f else 3f)) {
        Box(Modifier.shadow(6.dp, RoundedCornerShape(8.dp)).background(Color.White, RoundedCornerShape(8.dp)).padding(5.dp)) {
            ItemImage(Item(title = "", body = "", kind = kind, collection = "", imageUri = uri), Modifier.size(150.dp, 120.dp), 4.dp)
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).size(56.dp, 16.dp).rotate(-2f).background(Color(0x77A8A8A8)))
        Text("✕", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            .clip(CircleShape).background(Color(0x99000000)).tap("Remove image") { onRemove() }.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

/** A 40dp round button inside the header's action pill. */
@Composable
private fun HeaderBtn(desc: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).tap(desc, onClick), Alignment.Center) { content() }
}

/** The 3-dot menu surface: 252dp wide, 24dp corners, hanging under the header's top-right corner. */
@Composable
private fun MenuPanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.statusBarsPadding().padding(top = 60.dp, end = 16.dp).width(252.dp)
        .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(Sb.menu).padding(8.dp), content = content)
}

/** One menu row: icon and label, with an optional second line. */
@Composable
private fun MenuRow(icon: String, label: String, sub: String? = null, tint: Color = Sb.ink, iconColor: Color = tint, enabled: Boolean = true, onClick: () -> Unit) {
    val sans = LocalFonts.current.second
    Row(Modifier.fillMaxWidth().height(if (sub != null) 58.dp else 52.dp).clip(RoundedCornerShape(16.dp)).alpha(if (enabled) 1f else .4f)
        .tap(label) { if (enabled) onClick() }.padding(horizontal = 12.dp), Arrangement.spacedBy(14.dp), Alignment.CenterVertically) {
        Ic(icon, 22.dp, iconColor)
        Column {
            Text(label, color = tint, fontSize = 16.sp, lineHeight = 20.sp, fontFamily = sans)
            if (sub != null) Text(sub, color = Sb.mute, fontSize = 12.5.sp, lineHeight = 16.sp, fontFamily = sans)
        }
    }
}

@Composable
private fun MenuDivider() = Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Sb.menuLine))

/** The floating card the toolbar's text options, color and size open above the bar. Same look as the 3-dot menu. */
@Composable
private fun PanelCard(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape).background(Sb.menu)
        .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Sb.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = LocalFonts.current.second, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).tap("Close") { onClose() }, Alignment.Center) { Ic("close", 22.dp, Sb.ink) }
        }
        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** A rounded group of option buttons inside a panel. */
@Composable
private fun OptGroup(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(Sb.ink.copy(alpha = .07f)).padding(4.dp), verticalAlignment = Alignment.CenterVertically, content = content)

/** One option button; on tints it with the accent. */
@Composable
private fun RowScope.OptBtn(on: Boolean, desc: String, enabled: Boolean = true, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val acc = LocalSb.current.acc
    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = .2f) else Color.Transparent)
        .alpha(if (enabled) 1f else .35f).semantics { selected = on }.tap(desc) { if (enabled) onClick() }, Alignment.Center) { content(if (on) acc else Sb.ink) }
}

/** Text options: lists, alignment, bold, italic, underline, strikethrough, indent and outdent. */
@Composable
private fun TextOptionsPanel(prefix: String?, bold: Boolean, italic: Boolean, underline: Boolean, strike: Boolean, align: Char, indent: Int,
                             onBullet: () -> Unit, onNumber: () -> Unit, onAlign: (Char) -> Unit, onInline: (String) -> Unit, onUnderline: () -> Unit,
                             onIndent: (Int) -> Unit, onClose: () -> Unit) {
    PanelCard("Text options", onClose) {
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            OptGroup(Modifier.weight(2f)) {
                OptBtn(prefix == "- ", "Bullet list", onClick = onBullet) { c -> Ic("list", 22.dp, c) }
                OptBtn(prefix == "1. ", "Numbered list", onClick = onNumber) { c -> Ic("numbered", 22.dp, c) }
            }
            OptGroup(Modifier.weight(3f)) {
                OptBtn(align == 'l', "Align left", onClick = { onAlign('l') }) { c -> Ic("alignl", 22.dp, c) }
                OptBtn(align == 'c', "Align center", onClick = { onAlign('c') }) { c -> Ic("alignc", 22.dp, c) }
                OptBtn(align == 'r', "Align right", onClick = { onAlign('r') }) { c -> Ic("alignr", 22.dp, c) }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            OptGroup(Modifier.weight(4f)) {
                OptBtn(bold, "Bold", onClick = { onInline("**") }) { c -> Text("B", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c) }
                OptBtn(italic, "Italic", onClick = { onInline("*") }) { c -> Text("I", fontSize = 18.sp, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, color = c) }
                OptBtn(underline, "Underline", onClick = onUnderline) { c -> Text("U", fontSize = 18.sp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline, color = c) }
                OptBtn(strike, "Strikethrough", onClick = { onInline("~~") }) { c -> Text("S", fontSize = 18.sp, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.LineThrough, color = c) }
            }
            OptGroup(Modifier.weight(2f)) {
                OptBtn(false, "Indent", enabled = indent < 4, onClick = { onIndent(1) }) { c -> Ic("indent", 22.dp, c) }
                OptBtn(false, "Outdent", enabled = indent > 0, onClick = { onIndent(-1) }) { c -> Ic("outdent", 22.dp, c) }
            }
        }
    }
}

private val SIZE_CHOICES = listOf(12, 14, 16, 18, 20, 24, 28, 32, 40, 48)

/** Font size: pick a size for the selected text, or go back to the normal size. */
@Composable
private fun SizePanel(current: Int?, normal: Int, onPick: (Int?) -> Unit, onClose: () -> Unit) {
    val acc = LocalSb.current.acc
    PanelCard("Font size", onClose) {
        SIZE_CHOICES.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                row.forEach { n ->
                    val on = current == n
                    Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = .2f) else Sb.ink.copy(alpha = .07f))
                        .semantics { selected = on }.tap("Size $n") { onPick(n) }, Alignment.Center) {
                        Text("$n", fontSize = 17.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) acc else Sb.ink)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (current == null) acc.copy(alpha = .2f) else Sb.ink.copy(alpha = .07f))
            .semantics { selected = current == null }.tap("Normal size") { onPick(null) }, Alignment.Center) {
            Text("Normal ($normal)", fontSize = 15.sp, color = if (current == null) acc else Sb.ink)
        }
    }
}

private val COLOR_CHOICES = listOf("ff3b30", "ff9500", "ffcc00", "34c759", "00c7be", "0a84ff", "5e5ce6", "bf5af2", "ff2d92", "ffffff", "8e8e93", "000000")

private fun shadeColor(hue: Float, t: Float) = if (t < .5f) Color.hsv(hue, 1f, t * 2f) else Color.hsv(hue, 1f - (t - .5f) * 2f, 1f)
private fun Color.hex6() = "%02x%02x%02x".format((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())

/** A bar with a gradient and a round handle. onChange runs while dragging, onDone when the finger lifts. */
@Composable
private fun GradientSlider(value: Float, brush: Brush, handle: Color, onChange: (Float) -> Unit, onDone: () -> Unit) {
    var w by remember { mutableIntStateOf(1) }
    val change by rememberUpdatedState(onChange); val done by rememberUpdatedState(onDone)
    Box(Modifier.fillMaxWidth().height(34.dp).onSizeChanged { w = it.width.coerceAtLeast(1) }.clip(RoundedCornerShape(17.dp)).background(brush)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                change((down.position.x / size.width).coerceIn(0f, 1f)); down.consume()
                do {
                    val ev = awaitPointerEvent(); val c = ev.changes.firstOrNull() ?: break
                    change((c.position.x / size.width).coerceIn(0f, 1f)); c.consume()
                } while (ev.changes.any { it.pressed })
                done()
            }
        }) {
        Box(Modifier.offset { IntOffset((value * (w - 34.dp.roundToPx())).roundToInt(), 0) }.size(34.dp).padding(3.dp).clip(CircleShape).background(handle).border(2.dp, Color.White, CircleShape))
    }
}

/** Font color: a row of ready colors, plus a hue bar and a light-to-dark bar for any other color. */
@Composable
private fun ColorPanel(current: String?, onPick: (String?) -> Unit, onClose: () -> Unit) {
    val acc = LocalSb.current.acc
    var hue by remember { mutableFloatStateOf(210f) }; var shade by remember { mutableFloatStateOf(.5f) }
    PanelCard("Font color", onClose) {
        COLOR_CHOICES.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                row.forEach { hex ->
                    val on = current == hex
                    Box(Modifier.size(40.dp).clip(CircleShape).background(hexColor(hex)).border(if (on) 3.dp else 1.dp, if (on) acc else Sb.ink.copy(alpha = .25f), CircleShape)
                        .semantics { selected = on }.tap("Color $hex") { onPick(hex) })
                }
            }
        }
        GradientSlider(hue / 360f, Brush.horizontalGradient(listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it % 360f, 1f, 1f) }), Color.hsv(hue, 1f, 1f),
            { hue = it * 360f }, { onPick(shadeColor(hue, shade).hex6()) })
        GradientSlider(shade, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, 1f, 1f), Color.White)), shadeColor(hue, shade),
            { shade = it }, { onPick(shadeColor(hue, shade).hex6()) })
        Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (current == null) acc.copy(alpha = .2f) else Sb.ink.copy(alpha = .07f))
            .semantics { selected = current == null }.tap("Default color") { onPick(null) }, Alignment.Center) {
            Text("Default color", fontSize = 15.sp, color = if (current == null) acc else Sb.ink)
        }
    }
}


/** A normal notes editor: autosaves as you type, formatting toolbar, lists continue on Enter, undo and redo. id == 0 starts a new note. */
@Composable
fun EditorScreen(vm: ArchiveViewModel, nav: NavController, id: Long) {
    val t = LocalSb.current; val list by vm.items.collectAsState(); val scope = rememberCoroutineScope()
    val existing = remember(id) { vm.items.value.firstOrNull { it.id == id } }
    var curId by remember(id) { mutableLongStateOf(id) }
    var title by remember(id) { mutableStateOf(existing?.let { x -> if (bodyWithoutTitle(x.title, x.body) != x.body) "" else x.title } ?: "") }
    var body by remember(id) { mutableStateOf(TextFieldValue(existing?.body ?: "")) }
    var color by remember(id) { mutableIntStateOf(existing?.color ?: 0) }
    var kind by remember(id) { mutableStateOf(existing?.kind ?: Kind.Note) }
    var shelf by remember(id) { mutableStateOf(existing?.collection ?: vm.takePendingShelf() ?: "Inbox") }
    var tags by remember(id) { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var imgs by remember(id) { mutableStateOf(existing?.allImages() ?: emptyList()) }
    var source by remember(id) { mutableStateOf(existing?.source ?: "") }
    var more by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    val undo = remember { ArrayDeque<TextFieldValue>() }; val redo = remember { ArrayDeque<TextFieldValue>() }
    var lastPush by remember { mutableLongStateOf(0L) }
    val lock = remember { Mutex() }; var leaving by remember { mutableStateOf(false) }
    val editorStyle by vm.editorStyle.collectAsState()
    val scroll = rememberScrollState(); var viewportH by remember { mutableIntStateOf(0) }; var fieldTop by remember { mutableFloatStateOf(0f) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }; var focused by remember { mutableStateOf(false) }
    val activeSel = if (focused) body.selection else null
    val live = list.firstOrNull { it.id == curId }
    val baseLine = pageLine((live ?: existing)?.density ?: 1).value
    val view = remember(body.text, activeSel, t.acc, Sb.ink, baseLine) { mdRender(body.text, activeSel, t.acc, baseLine) }
    val viewNow by rememberUpdatedState(view)
    val md = remember(view) { VisualTransformation { s -> if (s.text == view.source) view.out else mdRender(s.text, null, t.acc, baseLine).out } }
    val clipboard = LocalClipboardManager.current
    var linkSheet by remember { mutableStateOf<TextRange?>(null) }; var linkUrl by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris -> uris.forEach { u -> vm.importImage(u) { imgs = imgs + it } } }
    fun addImage() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun doUndo() { if (undo.isNotEmpty()) { redo.addLast(body); body = undo.removeLast() } }
    fun doRedo() { if (redo.isNotEmpty()) { undo.addLast(body); body = redo.removeLast() } }
    /** Opens the link sheet for the current selection. The selection is remembered because the sheet takes focus from the text. */
    fun openLink() { linkUrl = body.link()?.second?.groupValues?.get(2) ?: ""; linkSheet = body.selection }

    /** Every body change goes through here so undo and redo see it. Quick typing is grouped into one step. */
    fun edit(new: TextFieldValue) {
        val next = continueList(body, smartEdit(body, new))
        if (next.text != body.text) {
            val now = System.currentTimeMillis()
            if (now - lastPush > 700 || undo.isEmpty()) { undo.addLast(body); if (undo.size > 100) undo.removeFirst() }
            lastPush = now; redo.clear()
        }
        body = next
    }

    var panel by remember { mutableIntStateOf(0) }   // 0 none, 1 text options, 2 font color, 3 font size
    var lastColor by remember { mutableStateOf<String?>(null) }
    /** The text a formatting button acts on: the selection, or the word at the cursor when nothing is selected. */
    fun target(): Pair<Int, Int>? {
        val s = body.text; var a = minOf(body.selection.start, body.selection.end).coerceIn(0, s.length); var b = maxOf(body.selection.start, body.selection.end).coerceIn(0, s.length)
        if (a == b) {
            var x = a; var y = a
            while (x > 0 && s[x - 1].isLetterOrDigit()) x--
            while (y < s.length && s[y].isLetterOrDigit()) y++
            if (x == y) return null
            a = x; b = y
        }
        return a to b
    }
    fun applySpan(f: (SpanAttrs) -> SpanAttrs) { val (a, b) = target() ?: return; edit(body.formatSpan(a, b, f)) }
    fun inlineOnTarget(marker: String) {
        val tg = target(); edit((if (tg != null) body.copy(selection = TextRange(tg.first, tg.second)) else body).toggleInline(marker))
    }
    fun toggleUnderline() {
        val (a, b) = target() ?: return
        val all = body.attrsIn(a, b).let { l -> l.isNotEmpty() && l.all { it.underline } }
        edit(body.formatSpan(a, b) { it.copy(underline = !all) })
    }
    /** Ticks or unticks the checklist line that starts at saved offset ls. */
    fun toggleCheck(ls: Int) {
        val s = body.text; if (ls + 4 > s.length) return
        edit(body.copy(text = s.replaceRange(ls + 3, ls + 4, if (s[ls + 3] == 'x') " " else "x")))
    }
    /** Double tap selects the word, triple tap the line, four taps everything. */
    fun selectByTaps(pos: Offset, taps: Int) {
        val lay = textLayout ?: return; val s = body.text; if (s.isEmpty()) return
        val t = lay.getOffsetForPosition(pos).coerceIn(0, lay.layoutInput.text.length)
        val range = when {
            taps >= 4 -> TextRange(0, s.length)
            taps == 3 -> {
                val o = viewNow.end(t).coerceIn(0, s.length)
                val ls = s.lastIndexOf('\n', o - 1) + 1; val le = s.indexOf('\n', o).let { if (it < 0) s.length else it }
                val line = s.substring(ls, le)
                val pre = (LINE_PREFIXES.firstOrNull { line.startsWith(it) } ?: NUM.find(line)?.value)?.length ?: 0
                TextRange(minOf(ls + pre, le), le)
            }
            else -> {
                val w = lay.getWordBoundary(t); val x = viewNow.start(w.start).coerceIn(0, s.length); val y = viewNow.end(w.end).coerceIn(0, s.length)
                TextRange(minOf(x, y), maxOf(x, y))
            }
        }
        if (!range.collapsed) body = body.copy(selection = range)
    }

    fun draft(): Item? {
        if (title.isBlank() && body.text.isBlank() && imgs.isEmpty()) return null
        val firstLine = body.text.lineSequence().firstOrNull { it.isNotBlank() }?.let { plainPreview(it) }?.takeIf { it.isNotBlank() }?.take(50)
        val base = vm.items.value.firstOrNull { it.id == curId } ?: existing ?: Item(title = "", body = "", kind = kind, collection = shelf)
        return base.copy(id = curId, title = title.ifBlank { firstLine ?: "Untitled" }, body = body.text, kind = kind, collection = shelf,
            color = color, imageUri = imgs.firstOrNull(), images = imgs.drop(1).joinToString("\n"), source = source,
            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }, savedAt = System.currentTimeMillis())
    }
    /** One save at a time, and a started save always finishes, so a new note can never be created twice. */
    suspend fun flush() = withContext(NonCancellable) {
        lock.withLock {
            val d = draft() ?: return@withLock
            val cur = vm.items.value.firstOrNull { it.id == curId }
            if (cur != null && d.copy(savedAt = cur.savedAt) == cur) return@withLock
            val r = vm.saveNow(d); if (curId == 0L) curId = r
        }
    }
    LaunchedEffect(title, body.text, color, kind, shelf, tags, imgs, source) { delay(600); flush() }
    fun leave() { if (leaving) return; leaving = true; scope.launch { flush(); nav.popBackStack() } }
    BackHandler { leave() }
    BackHandler(menu) { menu = false }
    BackHandler(panel != 0) { panel = 0 }

    Box(Modifier.fillMaxSize()) {
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).background(pageTint(color) ?: Color.Transparent).imePadding()) {
            val subtitle = (listOf(shelf) + tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }).joinToString(" · ")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Sb.card).tap("Save and go back") { leave() }, Alignment.Center) { Ic("back", 26.dp, Sb.ink) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    BasicTextField(title, { title = it.replace("\n", " ") }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp, fontFamily = headFont(), fontWeight = FontWeight.Medium, color = Sb.ink),
                        cursorBrush = SolidColor(t.acc),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (title.isEmpty()) Text("Title", color = Sb.mute.copy(alpha = .6f), fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp, fontFamily = headFont(), fontWeight = FontWeight.Medium)
                                inner()
                            }
                        })
                    Text(subtitle, fontSize = 12.5.sp, lineHeight = 16.sp, color = Sb.mute, fontFamily = LocalFonts.current.second, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // One pill holds the three actions: reading view, add image, more.
                Row(Modifier.height(44.dp).clip(CircleShape).background(Sb.card).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeaderBtn("Reading view", { scope.launch { flush(); if (curId != 0L) nav.navigate("item/$curId") } }) { Ic("book", 22.dp, Sb.ink) }
                    HeaderBtn("Add image", { addImage() }) { Ic("plus", 22.dp, t.acc) }
                    HeaderBtn("More options", { menu = true }) { Ic("more", 22.dp, Sb.ink) }
                }
            }
            Column(Modifier.weight(1f).onSizeChanged { viewportH = it.height }.verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (kind == Kind.Quote) { Field(source, { source = it }, "Source: who said it, or where it is from", 14.sp, headFont()); Spacer(Modifier.height(10.dp)) }
                if (imgs.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        imgs.forEachIndexed { i, u -> Polaroid(u, kind, i) { imgs = imgs - u } }
                    }
                }
                Box(Modifier.fillMaxWidth().onGloballyPositioned { fieldTop = it.positionInParent().y }
                    .pointerInput(id) {
                        // Tapping a ☐ / ☑ ticks it without moving the cursor or opening the keyboard.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val lay = textLayout
                            val hit = if (lay != null) checkboxAt(lay, viewNow, body.text, down.position, 8.dp.toPx()) else null
                            if (hit != null) {
                                down.consume()
                                val up = waitForUpOrCancellation(PointerEventPass.Initial)
                                if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) { up.consume(); toggleCheck(hit) }
                            }
                        }
                    }
                    .pointerInput(id) {
                        // Counts quick taps in the same spot. The text field handles the first two; this makes sure 2, 3 and 4 taps always select.
                        var taps = 0; var lastUp = 0L; var lastPos = Offset.Zero
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val up = waitForUpOrCancellation(PointerEventPass.Initial)
                            if (up == null || (up.position - down.position).getDistance() > viewConfiguration.touchSlop || up.uptimeMillis - down.uptimeMillis > 400) { taps = 0; return@awaitEachGesture }
                            taps = if (up.uptimeMillis - lastUp < 350 && (up.position - lastPos).getDistance() < viewConfiguration.touchSlop * 3f) taps + 1 else 1
                            lastUp = up.uptimeMillis; lastPos = up.position
                            if (taps >= 2) { val p = up.position; val c = taps; scope.launch { delay(70); selectByTaps(p, c) } }
                        }
                    }) {

                    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(handleColor = t.acc, backgroundColor = t.acc.copy(alpha = .3f))) {
                        BasicTextField(body, { edit(it) }, Modifier.fillMaxWidth().heightIn(min = 320.dp).onFocusChanged { focused = it.isFocused },
                            textStyle = TextStyle(fontSize = 17.sp, fontFamily = pageFont((live ?: existing)?.font ?: 0) ?: headFont(), color = Sb.ink, lineHeight = pageLine((live ?: existing)?.density ?: 1)),
                            cursorBrush = SolidColor(t.acc), visualTransformation = md, onTextLayout = { textLayout = it },
                            decorationBox = { inner -> Box { if (body.text.isEmpty()) Text("Write something…", color = Sb.mute, fontSize = 17.sp, fontFamily = headFont()); inner() } })
                    }
                    val lay = textLayout
                    if (editorStyle == 0 && focused && !body.selection.collapsed && lay != null && linkSheet == null)
                        SelectionBubble(body, lay, view.out.offsetMapping, fieldTop, scroll.value, viewportH, { m -> edit(body.toggleInline(m)) }, { openLink() },
                            { val a = minOf(body.selection.start, body.selection.end).coerceIn(0, body.text.length); val b = maxOf(body.selection.start, body.selection.end).coerceIn(0, body.text.length); clipboard.setText(AnnotatedString(body.text.substring(a, b))) })
                }
            }
            if (more) Column(Modifier.fillMaxWidth().background(Sb.card)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Sb.line))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorDots(color) { color = it }
                    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(5.dp)) {
                        Kind.entries.forEach { Chip(it.label, kind == it) { kind = it } }
                        Chip("Inbox", shelf == "Inbox") { shelf = "Inbox" }
                        vm.shelves.value.forEach { Chip(it.name, shelf == it.name) { shelf = it.name } }
                    }
                    InputBox { Field(tags, { tags = it }, "Tags, separated by commas", 15.sp, headFont()) }
                }
            }
            val tg = target(); val attrNow = tg?.let { (a, b) -> body.attrsIn(a, b) }.orEmpty()
            val sizeNow = attrNow.firstOrNull()?.size; val colorNow = attrNow.firstOrNull()?.color
            if (editorStyle == 0) BubbleBar(body.linePrefix(), undo.isNotEmpty(), redo.isNotEmpty(), (sizeNow ?: 17).toString(), (lastColor ?: colorNow)?.let { hexColor(it) } ?: t.acc, panel, { panel = it },
                { edit(body.toggleLine("> ")) }, { edit(body.insertDivider()) }, { edit(body.cycleHeading()) }, { edit(body.toggleLine("- [ ] ")) }, { doUndo() }, { doRedo() })
            else PillBar(body, { m -> edit(body.toggleInline(m)) }, { edit(body.cycleHeading()) }, { edit(body.toggleLine("- ")) },
                { edit(body.toggleLine("1. ")) }, { edit(body.toggleLine("- [ ] ")) })
        }
    }
    if (panel != 0 && editorStyle == 0) {
        // Floats above the bottom bar, whether or not the keyboard is open. A tap outside closes it; the bar stays tappable.
        val barGap = WindowInsets.ime.union(WindowInsets.navigationBars)
        Box(Modifier.fillMaxSize().windowInsetsPadding(barGap).padding(bottom = 76.dp)) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { panel = 0 })
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 6.dp)) {
                val tg2 = target(); val attrs2 = tg2?.let { (a, b) -> body.attrsIn(a, b) }.orEmpty()
                when (panel) {
                    1 -> {
                        val (ind, al) = body.lineState()
                        TextOptionsPanel(body.linePrefix(), body.enclosing("**") != null, body.enclosing("*") != null, attrs2.isNotEmpty() && attrs2.all { it.underline }, body.enclosing("~~") != null, al, ind,
                            { edit(body.toggleLine("- ")) }, { edit(body.toggleLine("1. ")) }, { c -> edit(body.setAlign(c)) }, { m -> inlineOnTarget(m) }, { toggleUnderline() },
                            { d -> edit(body.indentBy(d)) }, { panel = 0 })
                    }
                    2 -> ColorPanel(attrs2.firstOrNull()?.color, { hex -> if (hex != null) lastColor = hex; applySpan { it.copy(color = hex) } }, { panel = 0 })
                    else -> SizePanel(attrs2.firstOrNull()?.size, 17, { n -> applySpan { it.copy(size = n) } }, { panel = 0 })
                }
            }
        }
    }
    AnimatedVisibility(menu, Modifier.fillMaxSize(), enter = fadeIn(tween(120)), exit = fadeOut(tween(100))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).semantics { contentDescription = "Close menu" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menu = false })
    }
    AnimatedVisibility(menu, Modifier.align(Alignment.TopEnd),
        enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = .92f, transformOrigin = TransformOrigin(1f, 0f)), exit = fadeOut(tween(100))) {
        MenuPanel {
            MenuRow("sliders", if (more) "Hide details" else "Details", "Color, box, tags") { menu = false; more = !more }
            if (editorStyle == 1) {
                MenuDivider()
                MenuRow("undo", "Undo", enabled = undo.isNotEmpty()) { menu = false; doUndo() }
                MenuRow("redo", "Redo", enabled = redo.isNotEmpty()) { menu = false; doRedo() }
                MenuRow("quote", "Quote") { menu = false; edit(body.toggleLine("> ")) }
                MenuRow("divider", "Divider") { menu = false; edit(body.insertDivider()) }
                MenuDivider()
            }
            if (live != null) {
                MenuRow(if (live.pinned) "star" else "starline", if (live.pinned) "Unpin" else "Pin", iconColor = if (live.pinned) t.acc else Sb.ink) { menu = false; vm.togglePin(live) }
                MenuRow("layout", "Change layout") { menu = false; scope.launch { flush(); nav.navigate("layouts/${live.id}") } }
                MenuDivider()
                MenuRow("trash", "Delete", tint = Sb.danger) { menu = false; vm.delete(live); nav.popBackStack("home", false) }
            }
            MenuDivider()
            val words = body.text.split(Regex("\\s+")).count { it.isNotBlank() }
            Text("$words ${if (words == 1) "word" else "words"} · ${body.text.length} characters", color = Sb.mute, fontSize = 12.5.sp, fontFamily = LocalFonts.current.second,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp))
        }
    }
    linkSheet?.let { sel ->
        val sheetBody = body.copy(selection = sel)
        val existingLink = sheetBody.link() != null
        val clean = linkUrl.trim().filterNot { it.isWhitespace() || it == ')' }
        val fullUrl = if (clean.startsWith("http://", true) || clean.startsWith("https://", true)) clean else "https://$clean"
        val focus = remember { FocusRequester() }
        Sheet(if (existingLink) "Edit link" else "Add link", { linkSheet = null }, doneLabel = if (existingLink) "Save" else "Add link", enabled = clean.isNotEmpty(),
            primary = { edit(sheetBody.setLink(fullUrl)); linkSheet = null }) {
            LaunchedEffect(Unit) { focus.requestFocus() }
            InputBox { Field(linkUrl, { linkUrl = it }, "https://", 16.sp, headFont(), modifier = Modifier.focusRequester(focus)) }
            if (existingLink) Row { Chip("Remove link") { edit(sheetBody.removeLink()); linkSheet = null } }
        }
    }
    }
}
