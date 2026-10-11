package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
        }.tap(onClick = onClick).padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.kind == Kind.Image) ItemImage(item, Modifier.fillMaxWidth().height(70.dp), 8.dp)
        Text(item.title, fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(plainPreview(item.body), fontSize = 10.5.sp, lineHeight = 14.sp, maxLines = 6, color = Sb.ink.copy(alpha = .75f))
    }
}

/** A collection (or "All" / "Inbox"): title and count, search inside it, filters, then its notes. */
@Composable
fun ShelfScreen(vm: ArchiveViewModel, nav: NavController, name: String) {
    val t = LocalSb.current; val all by vm.items.collectAsState(); val shelves by vm.shelves.collectAsState()
    val shelf = shelves.firstOrNull { it.name == name }
    val navStyle by vm.navStyle.collectAsState(); var menuSheet by remember { mutableStateOf(false) }; var styleSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }; var inboxSheet by remember { mutableStateOf(false) }; var existed by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }; var kind by remember { mutableStateOf<Kind?>(null) }
    var kindMenu by remember { mutableStateOf(false) }; var sortMenu by remember { mutableStateOf(false) }; var more by remember { mutableStateOf(false) }
    var byTitle by rememberSaveable { mutableStateOf(false) }; var grid by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(shelf) { if (shelf != null) existed = true else if (existed) nav.popBackStack() }
    val inShelf = all.filter { name == "All" || (name == "Pinned" && it.pinned) || (name == "Recent" && it.savedAt > System.currentTimeMillis() - 7L * 86_400_000L) || it.collection == name }
    val shown = inShelf.filter { n -> (kind == null || n.kind == kind) && (q.isBlank() || listOf(n.title, n.body, n.tags.joinToString(" ")).any { it.contains(q, true) }) }
        .let { l -> if (byTitle) l.sortedBy { it.title.lowercase() } else l.sortedByDescending { it.savedAt } }
        .sortedByDescending { it.pinned }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = t.paper) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).tap("Go back") { nav.popBackStack() }, Alignment.Center) { Ic("back", 28.dp, Sb.ink) }
                            Text("scrapbook", fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Box {
                                Box(Modifier.size(48.dp).tap("More options") { more = true }, Alignment.Center) { Ic("more", 24.dp, Sb.ink) }
                                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                    if (shelf != null) DropdownMenuItem(text = { Text("Edit box") }, onClick = { more = false; editing = true })
                                    if (name == "Inbox") DropdownMenuItem(text = { Text("Change color") }, onClick = { more = false; inboxSheet = true })
                                    DropdownMenuItem(text = { Text("All boxes") }, onClick = { more = false; nav.navigate("collections") })
                                }
                            }
                        }
                    }
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(name, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 36.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                Text("  " + (if (inShelf.size == 1) "1 scrap" else "${inShelf.size} scraps"), fontSize = 14.sp, color = Sb.mute, modifier = Modifier.padding(bottom = 7.dp))
                            }
                            if (shelf != null && shelf.description.isNotBlank()) Text(shelf.description, fontSize = 14.sp, color = Sb.mute, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (shelf?.coverUri != null) item {
                        ItemImage(Item(title = "", body = "", kind = Kind.Image, collection = "", imageUri = shelf.coverUri, color = shelf.color), Modifier.fillMaxWidth().height(120.dp), 24.dp)
                    }
                    item {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Sb.line.copy(alpha = .55f)).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ic("search", 22.dp, Sb.mute); Spacer(Modifier.width(12.dp))
                            BasicTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Sb.ink), cursorBrush = SolidColor(t.acc),
                                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search in $name", color = Sb.mute, fontSize = 16.sp); inner() } })
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    Row(Modifier.tap("Filter by type") { kindMenu = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(kind?.let { it.label + "s" } ?: "All scraps", fontSize = 15.sp, color = Sb.mute); Ic("chevdown", 20.dp, Sb.mute)
                                    }
                                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                                        DropdownMenuItem(text = { Text("All scraps") }, onClick = { kindMenu = false; kind = null })
                                        Kind.entries.forEach { k -> DropdownMenuItem(text = { Text(k.label + "s") }, onClick = { kindMenu = false; kind = k }) }
                                    }
                                }
                                Box {
                                    Row(Modifier.tap("Change sort order") { sortMenu = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (byTitle) "Title" else "Latest", fontSize = 15.sp, color = Sb.mute); Ic("chevdown", 20.dp, Sb.mute)
                                    }
                                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                        DropdownMenuItem(text = { Text("Latest") }, onClick = { sortMenu = false; byTitle = false })
                                        DropdownMenuItem(text = { Text("Title") }, onClick = { sortMenu = false; byTitle = true })
                                    }
                                }
                            }
                            Row(Modifier.clip(RoundedCornerShape(26.dp)).background(Sb.line.copy(alpha = .6f)).padding(4.dp)) {
                                SegIcon("list", !grid) { grid = false }; SegIcon("grid", grid) { grid = true }
                            }
                        }
                    }
                    if (shown.isEmpty()) item { Text(if (q.isNotBlank() || kind != null) "Nothing matches." else "Nothing here yet. Tap New scrap to add the first one.", color = Sb.mute, fontSize = 15.sp, modifier = Modifier.padding(vertical = 30.dp)) }
                    else if (grid) items(shown.chunked(2)) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { NoteTile(it, nav, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    } else items(shown, key = { it.id }) { NoteRow(it, nav) }
                }
                val addScrap = { vm.pendingShelf = if (shelf != null) name else null; nav.navigate("edit/0") }
                if (navStyle == 1) BottomNav(Modifier.align(Alignment.BottomEnd), { menuSheet = true }, addScrap)
                else NewScrapPill(Modifier.align(Alignment.BottomEnd), addScrap)
            }
        }
        if (editing && shelf != null) ShelfSheet(vm, shelf) { editing = false }
        if (inboxSheet) InboxSheet(vm) { inboxSheet = false }
        if (menuSheet) MenuSheet(nav, { menuSheet = false }, { styleSheet = true }) { vm.pendingShelf = null }
        if (styleSheet) AppStyleSheet(vm) { styleSheet = false }
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

/** Search across titles, text, tags and collections, with type filters and highlighted matches. */
@Composable
fun SearchScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState(); val shelves by vm.shelves.collectAsState()
    var q by remember { mutableStateOf("") }; var kind by remember { mutableStateOf<Kind?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val res = all.filter { n -> (kind == null || n.kind == kind) && (q.isBlank() || listOf(n.title, n.body, n.collection, n.tags.joinToString(" ")).any { it.contains(q, true) }) }
        .sortedByDescending { it.savedAt }
    val shelfHits = if (q.isBlank()) emptyList() else shelves.filter { it.name.contains(q, true) || it.description.contains(q, true) }
    val mark = t.acc.copy(alpha = .28f)
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).tap("Go back") { nav.popBackStack() }, Alignment.Center) { Ic("back", 28.dp, Sb.ink) }
                Row(Modifier.weight(1f).padding(end = 12.dp).clip(RoundedCornerShape(28.dp)).background(Sb.line.copy(alpha = .55f)).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ic("search", 22.dp, Sb.mute); Spacer(Modifier.width(12.dp))
                    BasicTextField(q, { q = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Sb.ink), cursorBrush = SolidColor(t.acc),
                        decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search scraps, tags, boxes", color = Sb.mute, fontSize = 16.sp); inner() } })
                    if (q.isNotEmpty()) Box(Modifier.size(28.dp).tap("Clear search") { q = "" }, Alignment.Center) { Text("✕", color = Sb.mute, fontSize = 14.sp) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), Arrangement.spacedBy(8.dp)) {
                Chip("All", kind == null) { kind = null }
                Kind.entries.forEach { k -> Chip(k.label + "s", kind == k) { kind = k } }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (shelfHits.isNotEmpty()) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                        Text("Boxes", fontSize = 13.sp, color = Sb.mute)
                        shelfHits.forEach { s -> Chip(s.name) { nav.navigate("shelf/${s.name}") } }
                    }
                }
                item { Text(if (res.size == 1) "1 result" else "${res.size} results", fontSize = 13.sp, color = Sb.mute) }
                items(res, key = { it.id }) { n ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Sb.card).tap("Open ${n.title}") { nav.navigate("edit/${n.id}") }.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(hl(n.title, q, mark), fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 18.sp)
                        Text(hl(plainPreview(bodyWithoutTitle(n.title, n.body)), q, mark), fontSize = 14.sp, color = Sb.mute, maxLines = 2)
                        Text("${n.collection} · ${n.kind.label}", fontSize = 12.sp, color = Sb.mute, modifier = Modifier.padding(top = 4.dp))
                    }
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
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 20.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).tap("Close") { nav.popBackStack() }, Alignment.Center) { Text("✕", fontSize = 20.sp, color = Sb.ink) }
                Text("From your archive", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 18.sp)
                Text(if (queue.isEmpty()) "0/0" else "${minOf(i + 1, queue.size)}/${queue.size}", Modifier.width(48.dp), textAlign = TextAlign.End, fontSize = 13.sp, color = Sb.mute)
            }
            Spacer(Modifier.height(24.dp))
            if (cur == null) Text(if (queue.isEmpty()) "Nothing saved yet. Old scraps will show up here." else "That's everything for now.", fontFamily = headFont(), fontSize = 20.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 80.dp))
            else Box(Modifier.fillMaxWidth().height(330.dp)) {
                Box(Modifier.matchParentSize().padding(horizontal = 10.dp).rotate(-5f).clip(RoundedCornerShape(32.dp)).background(Sb.line))
                Box(Modifier.matchParentSize().padding(horizontal = 10.dp).rotate(4f).clip(RoundedCornerShape(32.dp)).background(Sb.line.copy(alpha = .6f)))
                Column(Modifier.matchParentSize().offset { IntOffset(x.roundToInt(), 0) }.rotate(x / 25f).shadow(8.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp)).background(Sb.card)
                    .pointerInput(i) { detectDragGestures(onDragEnd = { if (x > 200f) next(true) else if (x < -200f) next(false) else x = 0f }) { c, d -> c.consume(); x += d.x } }
                    .padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${cur.collection} · ${cur.kind.label}", fontSize = 13.sp, color = Sb.mute)
                    Text(cur.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp)
                    Text(plainPreview(bodyWithoutTitle(cur.title, cur.body), "\n"), fontFamily = headFont(), fontSize = 16.sp, lineHeight = 24.sp, color = Sb.ink.copy(alpha = .8f), maxLines = 8)
                }
            }
            if (cur != null) {
                Text("You saved this ${ago(cur.savedAt)}.", fontSize = 15.sp, color = Sb.mute, modifier = Modifier.padding(top = 24.dp))
                Row(Modifier.padding(top = 18.dp), Arrangement.spacedBy(36.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Sb.line.copy(alpha = .7f)).tap("Let it rest") { next(false) }, Alignment.Center) { Text("✕", fontSize = 22.sp, color = Sb.ink) }
                        Text("Let it rest", fontSize = 12.sp, color = Sb.mute)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Sb.ink).tap("Keep close") { next(true) }, Alignment.Center) { Text("✓", fontSize = 24.sp, color = Sb.card) }
                        Text("Keep close", fontSize = 12.sp, color = Sb.mute)
                    }
                }
            }
        }
    }
}
