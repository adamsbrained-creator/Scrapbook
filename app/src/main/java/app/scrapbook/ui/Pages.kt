package app.scrapbook.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class PageStyle(val family: FontFamily?, val gap: Dp, val line: TextUnit, val showMeta: Boolean)
val LocalPage = compositionLocalOf { PageStyle(null, 10.dp, 22.sp, true) }
val LocalCanvas = compositionLocalOf<(String) -> Unit> { { } }
private fun Item.style() = PageStyle(
    when (font) { 1 -> FontFamily.Serif; 2 -> FontFamily.SansSerif; 3 -> FontFamily.Monospace; else -> null },
    when (density) { 0 -> 16.dp; 2 -> 6.dp; else -> 10.dp }, when (density) { 0 -> 25.sp; 2 -> 19.sp; else -> 22.sp }, showMeta)

private fun date(ms: Long) = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(ms))
@Composable private fun Meta(text: String) = Text(text, fontSize = 10.5.sp, color = Sb.mute)
@Composable private fun Title(text: String, size: TextUnit = 26.sp, align: TextAlign = TextAlign.Start) =
    Text(text, fontFamily = LocalPage.current.family ?: headFont(), fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 1.15f, textAlign = align, modifier = Modifier.semantics { heading() })
/** Web links in a text become tappable and open in the browser. */
@Composable
private fun linkify(text: String): AnnotatedString {
    val acc = LocalSb.current.acc
    return buildAnnotatedString {
        var i = 0
        findUrls(text).forEach { r ->
            val url = text.substring(r.first, r.last + 1)
            append(text.substring(i, r.first))
            withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = acc, textDecoration = TextDecoration.Underline)))) { append(url) }
            i = r.last + 1
        }
        append(text.substring(i))
    }
}

/** Lets a tapped checklist line rewrite the note body: (oldLine, newLine). */
val LocalToggle = compositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/** Body text with light formatting: # heading, ## subheading, > quote, - bullet, - [ ] checklist, --- divider. */
@Composable
private fun Body(text: String) {
    val st = LocalPage.current; val toggle = LocalToggle.current; val acc = LocalSb.current.acc
    val fam = st.family ?: headFont(); val head = st.family ?: headFont()
    Column {
        text.lines().forEach { l ->
            when {
                l.startsWith("## ") -> Text(linkify(l.drop(3)), fontFamily = head, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
                l.startsWith("# ") -> Text(linkify(l.drop(2)), fontFamily = head, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, modifier = Modifier.padding(top = 8.dp))
                l.startsWith("> ") -> Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(acc)); Spacer(Modifier.width(10.dp))
                    Text(linkify(l.drop(2)), fontFamily = fam, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = st.line)
                }
                l == "---" -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Sb.line))
                l.startsWith("- [ ] ") || l.startsWith("- [x] ") -> {
                    val done = l.startsWith("- [x] ")
                    Row(Modifier.tap { toggle(l, (if (done) "- [ ] " else "- [x] ") + l.drop(6)) }.padding(vertical = 3.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                        Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(if (done) acc else Color.Transparent).border(1.5.dp, acc, RoundedCornerShape(4.dp)))
                        Text(linkify(l.drop(6)), fontFamily = fam, fontSize = 14.sp, lineHeight = st.line, color = if (done) Sb.mute else Sb.ink, textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None)
                    }
                }
                l.startsWith("- ") -> Row(Modifier.padding(vertical = 1.dp), Arrangement.spacedBy(8.dp)) {
                    Text("•", fontSize = 14.sp, color = acc); Text(linkify(l.drop(2)), fontFamily = fam, fontSize = 14.sp, lineHeight = st.line)
                }
                l.isBlank() -> Spacer(Modifier.height(8.dp))
                else -> Text(linkify(l), fontFamily = fam, fontSize = 14.sp, lineHeight = st.line)
            }
        }
    }
}

@Composable
private fun Drag(start: Offset, rot: Float, onDone: (Offset) -> Unit, content: @Composable () -> Unit) {
    var o by remember(start) { mutableStateOf(start) }
    val done by rememberUpdatedState(onDone)
    Box(Modifier.offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }.rotate(rot)
        .pointerInput(Unit) { detectDragGestures(onDragEnd = { done(o) }) { c, d -> c.consume(); o += d } }) { content() }
}

/** One item, rendered by its layout. Content is shared across layouts, so switching never loses anything. */
@Composable
fun ItemPage(item: Item, all: List<Item>) = CompositionLocalProvider(LocalPage provides item.style()) { Column { PageBody(item, all); InlineImages(item) } }

/** Images attached to a note appear under its text on the text-first layouts. */
@Composable
private fun InlineImages(item: Item) {
    if (item.layout !in listOf(Layout.Classic, Layout.Journal, Layout.TwoColumn, Layout.Reference, Layout.Quote)) return
    item.allImages().forEach { u -> ItemImage(item.copy(imageUri = u), Modifier.fillMaxWidth().height(200.dp).padding(top = LocalPage.current.gap)) }
}

@Composable
private fun PageBody(item: Item, all: List<Item>) {
    val st = LocalPage.current; val gap = st.gap
    val defaults = listOf(Offset(30f, 30f), Offset(150f, 250f), Offset(40f, 300f))
    val saved = remember(item.canvas) { item.canvas.split(";").mapNotNull { s -> s.split(",").takeIf { it.size == 2 }?.let { Offset(it[0].toFloatOrNull() ?: 0f, it[1].toFloatOrNull() ?: 0f) } } }
    val pos = defaults.mapIndexed { i, d -> saved.getOrNull(i) ?: d }
    val canvasSave = LocalCanvas.current
    fun moved(i: Int, o: Offset) = canvasSave(pos.mapIndexed { j, q -> if (j == i) o else q }.joinToString(";") { "${it.x},${it.y}" })
    val t = LocalSb.current; val r = RoundedCornerShape(t.radius)
    val tags = item.tags.joinToString().ifBlank { "—" }
    when (item.layout) {
        Layout.Classic -> Column(verticalArrangement = Arrangement.spacedBy(gap)) { if (st.showMeta) Meta(item.collection); Title(item.title); Body(item.body) }
        Layout.Journal -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            if (st.showMeta) Meta(date(item.savedAt)); Title(item.title, 28.sp); Body(item.body)
            if (st.showMeta) Row(Modifier.padding(top = 8.dp), Arrangement.spacedBy(8.dp)) {
                listOf("Collection" to item.collection, "Kind" to item.kind.name, "Tags" to tags).forEach {
                    Column(Modifier.weight(1f).clip(r).background(t.tint).padding(10.dp)) {
                        Meta(it.first); Text(it.second, fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
        Layout.Gallery -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            val gal = item.allImages()
            if (gal.size > 1) {
                val ps = rememberPagerState { gal.size }
                HorizontalPager(ps, Modifier.fillMaxWidth().height(260.dp), pageSpacing = 8.dp) { i -> ItemImage(item.copy(imageUri = gal[i]), Modifier.fillMaxSize(), t.radius + 8.dp) }
                Text("${ps.currentPage + 1} / ${gal.size}", Modifier.fillMaxWidth(), fontSize = 11.sp, color = Sb.mute, textAlign = TextAlign.Center)
            } else ItemImage(item, Modifier.fillMaxWidth().height(260.dp), t.radius + 8.dp)
            Text(item.title, Modifier.fillMaxWidth(), fontFamily = headFont(), fontStyle = FontStyle.Italic, fontSize = 13.sp, textAlign = TextAlign.Center, color = Sb.mute)
            Body(item.body)
        }
        Layout.TwoColumn -> {
            val multi = item.body.lines().size > 1; val sep = if (multi) "\n" else " "
            val w = if (multi) item.body.lines() else item.body.split(" "); val h = (w.size + 1) / 2
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Title(item.title)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) { Body(w.take(h).joinToString(sep)) }
                    Column(Modifier.weight(1f)) { Body(w.drop(h).joinToString(sep)) }
                }
            }
        }
        Layout.Quote -> Column(Modifier.fillMaxWidth().padding(top = 28.dp), Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
            Title("“${item.body}”", 24.sp, TextAlign.Center); Meta(item.source.ifBlank { item.title })
        }
        Layout.Reference -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Title(item.title, 22.sp); Body(item.body)
            if (st.showMeta) Column(Modifier.fillMaxWidth().clip(r).background(Sb.card).border(1.dp, Sb.line, r).padding(horizontal = 12.dp)) {
                listOf("Collection" to item.collection, "Kind" to item.kind.name, "Saved" to date(item.savedAt), "Tags" to tags).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween) { Meta(k); Text(v, fontSize = 11.5.sp) }
                }
            }
        }
        Layout.Collection -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            ItemImage(item, Modifier.fillMaxWidth().height(130.dp)); Title(item.title, 24.sp); Body(item.body)
            Meta("More in ${item.collection}")
            val rel = all.filter { it.collection == item.collection && it.id != item.id }
            if (rel.isEmpty()) Meta("Nothing else here yet")
            rel.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { Text(it.title, Modifier.weight(1f).clip(r).background(Sb.notes[it.color % 5]).padding(10.dp), fontFamily = headFont(), fontSize = 12.sp) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Layout.Blank -> Box(Modifier.fillMaxWidth().height(420.dp).clip(r).drawBehind {
            var y = 0f; while (y < size.height) { var x = 0f; while (x < size.width) { drawCircle(Color(0xFFCFCABB), 1.5f, Offset(x, y)); x += 48f }; y += 48f }
        }) {
            Drag(pos[0], -3f, { moved(0, it) }) { Column(Modifier.width(150.dp).clip(r).background(Sb.notes[item.color % 5]).padding(12.dp)) { Title(item.title, 18.sp) } }
            Drag(pos[1], 2f, { moved(1, it) }) { Column(Modifier.width(180.dp).clip(r).background(Sb.card).border(1.dp, Sb.line, r).padding(12.dp)) { Body(item.body) } }
            Drag(pos[2], -4f, { moved(2, it) }) { ItemImage(item, Modifier.size(90.dp, 100.dp)) }
        }
    }
}

@Composable
private fun Act(label: String, onClick: () -> Unit) {
    Text(label, Modifier.clip(RoundedCornerShape(12.dp)).background(Sb.card).border(1.dp, Sb.line, RoundedCornerShape(12.dp))
        .tap(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
}

/** Reading view: a quiet page. Edit and share sit in the top bar, everything else in the menu. */
@Composable
fun ItemScreen(vm: ArchiveViewModel, nav: NavController, id: Long) {
    val t = LocalSb.current; val list by vm.items.collectAsState()
    val item = list.firstOrNull { it.id == id }
    var sheet by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = t.paper) { pad ->
            if (item != null) Column(Modifier.fillMaxSize().padding(pad).background(pageTint(item.color) ?: Color.Transparent)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).tap("Go back") { nav.popBackStack() }, Alignment.Center) { Ic("back", 28.dp, Sb.ink) }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(item.collection, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("${item.kind.name} · ${date(item.savedAt)}", fontSize = 11.sp, color = Sb.mute, maxLines = 1)
                    }
                    Box(Modifier.size(44.dp).tap("Edit") { nav.navigate("edit/${item.id}") }, Alignment.Center) { Ic("edit", 24.dp, t.acc) }
                    Box(Modifier.size(44.dp).tap("Share") {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, item.title); putExtra(Intent.EXTRA_TEXT, item.title + "\n\n" + item.body)
                        }, "Share note"))
                    }, Alignment.Center) { Ic("share", 24.dp, Sb.ink) }
                    Box {
                        Box(Modifier.size(44.dp).tap("More options") { menu = true }, Alignment.Center) { Ic("more", 24.dp, Sb.ink) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (item.pinned) "Unpin" else "Pin") }, onClick = { menu = false; vm.togglePin(item) })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; vm.duplicate(item) { nav.navigate("item/$it") } })
                            DropdownMenuItem(text = { Text("Add to collection") }, onClick = { menu = false; panel = "move" })
                            DropdownMenuItem(text = { Text("Tags") }, onClick = { menu = false; panel = "tags" })
                            DropdownMenuItem(text = { Text("Change layout") }, onClick = { menu = false; nav.navigate("layouts/${item.id}") })
                            DropdownMenuItem(text = { Text("Page style") }, onClick = { menu = false; sheet = true })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; vm.delete(item); nav.popBackStack("home", false) })
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Sb.line))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
                    CompositionLocalProvider(LocalToggle provides { a, b -> vm.save(item.copy(body = item.body.replaceFirst(a, b))) },
                        LocalCanvas provides { s -> vm.save(item.copy(canvas = s)) }) { ItemPage(item, list) }
                }
            }
        }
        if (item != null) {
            if (sheet) CustomizeSheet(item, vm) { sheet = false }
            if (panel == "move") MoveSheet(item, vm) { panel = null }
            if (panel == "tags") TagSheet(item, vm) { panel = null }
        }
    }
}
