package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*
import kotlin.math.roundToInt

private const val FOLD = 40f
private val FoldShape = GenericShape { s, _ ->
    moveTo(0f, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height - FOLD); lineTo(s.width - FOLD, s.height); lineTo(0f, s.height); close()
}

/** Sticky note with a flat folded corner. */
@Composable
fun Sticky(item: Item, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(FoldShape).background(Sb.notes[item.color % 5])
        .drawWithContent {
            drawContent()
            val p = Path().apply { moveTo(size.width - FOLD, size.height - FOLD); lineTo(size.width, size.height - FOLD); lineTo(size.width - FOLD, size.height); close() }
            drawPath(p, Color(0x22000000))
        }.clickable(onClick = onClick).padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.kind == Kind.Image) ItemImage(item, Modifier.fillMaxWidth().height(70.dp), 8.dp)
        Text(item.title, fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(item.body, fontSize = 10.5.sp, lineHeight = 14.sp, maxLines = 6, color = Sb.ink.copy(alpha = .75f))
    }
}

/** A collection as a wall of stickies, filtered by type. name == "All" shows everything. */
@Composable
fun ShelfScreen(vm: ArchiveViewModel, nav: NavController, name: String) {
    val t = LocalSb.current; val all by vm.items.collectAsState()
    var kind by remember { mutableStateOf<Kind?>(null) }
    val shown = all.filter { (name == "All" || it.collection == name) && (kind == null || it.kind == kind) }
    Scaffold(containerColor = t.paper, bottomBar = { BottomBar({ nav.popBackStack() }, { nav.navigate("edit/0") }, { nav.navigate("search") }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 15.dp)) {
            Row(Modifier.padding(vertical = 10.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                Text("‹", fontSize = 22.sp, modifier = Modifier.clickable { nav.popBackStack() })
                Column {
                    Text(name, fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Text("${shown.size} items", fontSize = 10.5.sp, color = Sb.mute)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(5.dp)) {
                Chip("All", kind == null) { kind = null }
                Kind.entries.forEach { Chip(it.name, kind == it) { kind = it } }
            }
            Row(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), Arrangement.spacedBy(8.dp)) {
                listOf(0, 1).forEach { col ->
                    Column(Modifier.weight(1f)) { shown.filterIndexed { i, _ -> i % 2 == col }.forEach { Sticky(it) { nav.navigate("item/${it.id}") } } }
                }
            }
        }
    }
}

private fun hl(text: String, q: String, mark: Color): AnnotatedString = buildAnnotatedString {
    if (q.isBlank()) { append(text); return@buildAnnotatedString }
    val lo = text.lowercase(); val ql = q.lowercase(); var i = 0
    while (true) {
        val j = lo.indexOf(ql, i)
        if (j < 0) { append(text.substring(i)); break }
        append(text.substring(i, j)); withStyle(SpanStyle(background = mark)) { append(text.substring(j, j + q.length)) }; i = j + q.length
    }
}

/** Global search over titles, body, tags and collections, with type filters and highlighted matches. */
@Composable
fun SearchScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState()
    var q by remember { mutableStateOf("") }; var kind by remember { mutableStateOf<Kind?>(null) }
    val res = all.filter { i -> (kind == null || i.kind == kind) && (q.isBlank() || listOf(i.title, i.body, i.collection, i.tags.joinToString(" ")).any { it.contains(q, true) }) }
    val mark = Color(0xFFF1E2A8)
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 15.dp)) {
            Row(Modifier.padding(vertical = 10.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                Text("‹", fontSize = 22.sp, modifier = Modifier.clickable { nav.popBackStack() })
                BasicTextField(q, { q = it }, Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEFEDE6)).padding(12.dp),
                    singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Sb.ink), cursorBrush = SolidColor(t.acc),
                    decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search titles, tags, quotes…", color = Sb.mute, fontSize = 13.sp); inner() } })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(5.dp)) {
                Chip("All", kind == null) { kind = null }
                Kind.entries.forEach { Chip(it.name, kind == it) { kind = it } }
            }
            Text("${res.size} results", fontSize = 10.5.sp, color = Sb.mute, modifier = Modifier.padding(vertical = 8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                res.forEach { i ->
                    Row(Modifier.fillMaxWidth().clickable { nav.navigate("item/${i.id}") }.padding(vertical = 9.dp), Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
                        ItemImage(i, Modifier.size(40.dp), 10.dp)
                        Column {
                            Text(hl(i.title, q, mark), fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(hl(i.body, q, mark), fontSize = 10.5.sp, color = Sb.mute, maxLines = 1)
                            Text("${i.collection} · ${i.kind.name}", fontSize = 9.5.sp, color = Sb.mute)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Sb.line))
                }
            }
        }
    }
}

private fun ago(ms: Long): String {
    val d = (System.currentTimeMillis() - ms) / 86_400_000L
    return when { d < 1 -> "today"; d < 30 -> "$d days ago"; else -> "${d / 30} months ago" }
}

/** Swipe through old finds: right keeps one close (pins it), left lets it rest. No notifications, no pressure. */
@Composable
fun RediscoverScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState()
    val queue = remember(all.isNotEmpty()) { all.sortedBy { it.savedAt } }
    var i by remember { mutableIntStateOf(0) }; var x by remember { mutableFloatStateOf(0f) }
    fun next(keep: Boolean) { queue.getOrNull(i)?.let { if (keep && !it.pinned) vm.togglePin(it) }; i++; x = 0f }
    val cur = queue.getOrNull(i)
    Scaffold(containerColor = t.tint.copy(alpha = .5f)) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 18.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), Arrangement.SpaceBetween) {
                Text("✕", fontSize = 16.sp, modifier = Modifier.clickable { nav.popBackStack() })
                Text("From your archive", fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("${minOf(i + 1, queue.size)}/${queue.size}", fontSize = 11.sp, color = Sb.mute)
            }
            Spacer(Modifier.height(24.dp))
            if (cur == null) Text("That's everything for now.", fontFamily = headFont(), fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 80.dp))
            else Box(Modifier.fillMaxWidth().height(300.dp)) {
                Box(Modifier.matchParentSize().padding(horizontal = 8.dp).rotate(-5f).clip(RoundedCornerShape(20.dp)).background(Sb.notes[2]))
                Box(Modifier.matchParentSize().padding(horizontal = 8.dp).rotate(4f).clip(RoundedCornerShape(20.dp)).background(Sb.notes[1]))
                Column(Modifier.matchParentSize().offset { IntOffset(x.roundToInt(), 0) }.rotate(x / 25f).clip(RoundedCornerShape(20.dp)).background(Sb.notes[cur.color % 5])
                    .pointerInput(i) { detectDragGestures(onDragEnd = { if (x > 200f) next(true) else if (x < -200f) next(false) else x = 0f }) { c, d -> c.consume(); x += d.x } }
                    .padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${cur.collection} · ${cur.kind.name}", fontSize = 10.5.sp, color = Sb.mute)
                    Text(cur.title, fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp)
                    Text(cur.body, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 14.sp, lineHeight = 21.sp)
                }
            }
            if (cur != null) {
                Text("You saved this ${ago(cur.savedAt)}.", fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 14.sp, modifier = Modifier.padding(top = 26.dp))
                Row(Modifier.padding(top = 18.dp), Arrangement.spacedBy(24.dp)) { RoundButton("✕") { next(false) }; RoundButton("✓", dark = true) { next(true) } }
                Text("Let it rest · Keep close", fontSize = 10.5.sp, color = Sb.mute, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}
