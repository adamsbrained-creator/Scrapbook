package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
private fun Item.style() = PageStyle(
    when (font) { 1 -> FontFamily.Serif; 2 -> FontFamily.SansSerif; 3 -> FontFamily.Monospace; else -> null },
    when (density) { 0 -> 16.dp; 2 -> 6.dp; else -> 10.dp }, when (density) { 0 -> 25.sp; 2 -> 19.sp; else -> 22.sp }, showMeta)

private fun date(ms: Long) = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(ms))
@Composable private fun Meta(text: String) = Text(text, fontSize = 10.5.sp, color = Sb.mute)
@Composable private fun Title(text: String, size: TextUnit = 26.sp, align: TextAlign = TextAlign.Start) =
    Text(text, fontFamily = LocalPage.current.family ?: headFont(), fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 1.15f, textAlign = align)
/** Lets a tapped checklist line rewrite the note body: (oldLine, newLine). */
val LocalToggle = compositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/** Body text. Lines starting "- [ ] " or "- [x] " become tappable checklist rows. */
@Composable
private fun Body(text: String) {
    val st = LocalPage.current; val toggle = LocalToggle.current; val fam = st.family ?: FontFamily.Serif
    if (text.lines().none { it.startsWith("- [ ] ") || it.startsWith("- [x] ") }) { Text(text, fontFamily = fam, fontSize = 14.sp, lineHeight = st.line); return }
    Column {
        text.lines().forEach { l ->
            if (!(l.startsWith("- [ ] ") || l.startsWith("- [x] "))) Text(l, fontFamily = fam, fontSize = 14.sp, lineHeight = st.line)
            else {
                val done = l.startsWith("- [x] ")
                Row(Modifier.clickable { toggle(l, (if (done) "- [ ] " else "- [x] ") + l.drop(6)) }.padding(vertical = 3.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                    Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(if (done) LocalSb.current.acc else Color.Transparent).border(1.5.dp, LocalSb.current.acc, RoundedCornerShape(4.dp)))
                    Text(l.drop(6), fontFamily = fam, fontSize = 14.sp, lineHeight = st.line, color = if (done) Sb.mute else Sb.ink, textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None)
                }
            }
        }
    }
}

@Composable
private fun Drag(x: Float, y: Float, rot: Float, content: @Composable () -> Unit) {
    var o by remember { mutableStateOf(Offset(x, y)) }
    Box(Modifier.offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }.rotate(rot)
        .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); o += d } }) { content() }
}

/** One item, rendered by its layout. Content is shared across layouts, so switching never loses anything. */
@Composable
fun ItemPage(item: Item, all: List<Item>) = CompositionLocalProvider(LocalPage provides item.style()) { PageBody(item, all) }

@Composable
private fun PageBody(item: Item, all: List<Item>) {
    val st = LocalPage.current; val gap = st.gap
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
            ItemImage(item, Modifier.fillMaxWidth().height(260.dp), t.radius + 8.dp)
            Text(item.title, Modifier.fillMaxWidth(), fontFamily = headFont(), fontStyle = FontStyle.Italic, fontSize = 13.sp, textAlign = TextAlign.Center, color = Sb.mute)
            Body(item.body)
        }
        Layout.TwoColumn -> {
            val w = item.body.split(" "); val h = w.size / 2
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Title(item.title)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) { Body(w.take(h).joinToString(" ")) }
                    Column(Modifier.weight(1f)) { Body(w.drop(h).joinToString(" ")) }
                }
            }
        }
        Layout.Quote -> Column(Modifier.fillMaxWidth().padding(top = 28.dp), Arrangement.spacedBy(16.dp), Alignment.CenterHorizontally) {
            Title("“${item.body}”", 24.sp, TextAlign.Center); Meta(item.title)
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
            Drag(30f, 30f, -3f) { Column(Modifier.width(150.dp).clip(r).background(Sb.notes[item.color % 5]).padding(12.dp)) { Title(item.title, 18.sp) } }
            Drag(150f, 250f, 2f) { Column(Modifier.width(180.dp).clip(r).background(Sb.card).border(1.dp, Sb.line, r).padding(12.dp)) { Body(item.body) } }
            Drag(40f, 300f, -4f) { FlatImage(item.color + 1, Modifier.size(90.dp, 100.dp)) }
        }
    }
}

@Composable
private fun RowScope.Act(label: String, onClick: () -> Unit) {
    Text(label, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Sb.card).border(1.dp, Sb.line, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(vertical = 10.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
}

/** Quiet reading view with metadata on top and actions at the bottom. */
@Composable
fun ItemScreen(vm: ArchiveViewModel, nav: NavController, id: Long) {
    val t = LocalSb.current; val list by vm.items.collectAsState()
    val item = list.firstOrNull { it.id == id }
    var sheet by remember { mutableStateOf(false) }
    Scaffold(containerColor = t.paper) { pad ->
        if (item != null) Box(Modifier.fillMaxSize().padding(pad).background(Sb.notes[item.color % 5].copy(alpha = .4f))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), Arrangement.SpaceBetween) {
                    Text("‹ Back", fontSize = 13.sp, modifier = Modifier.clickable { nav.popBackStack() })
                    Meta("${item.collection} · ${item.kind.name} · ${date(item.savedAt)}")
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    CompositionLocalProvider(LocalToggle provides { a, b -> vm.save(item.copy(body = item.body.replaceFirst(a, b))) }) { ItemPage(item, list) }
                }
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp), Arrangement.spacedBy(6.dp)) {
                    Act("Edit") { nav.navigate("edit/${item.id}") }
                    Act(if (item.pinned) "Unpin" else "Pin") { vm.togglePin(item) }
                    Act("Layout") { nav.navigate("layouts/${item.id}") }
                    Act("Style") { sheet = true }
                    Act("Delete") { vm.delete(item); nav.popBackStack("home", false) }
                }
            }
            if (sheet) CustomizeSheet(item, vm) { sheet = false }
        }
    }
}
