package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TAB_COLORS = listOf(Color(0xFF3DD9D6), Color(0xFFFF5A44), Color(0xFFFFC107), Color(0xFF00A97F), Color(0xFF8E7CF0))
val TAB_GRAY = Color(0xFFB4B4B4)

private fun fmtDate(ms: Long, long: Boolean): String {
    val d = Date(ms); val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(d)
    return if (day.format(d) == day.format(Date())) (if (long) "Today · $time" else time) else SimpleDateFormat("MMM d", Locale.getDefault()).format(d)
}

@Composable
private fun TopBtn(kind: String, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).tap(desc, onClick), Alignment.Center) { Ic(kind, 24.dp, Sb.ink) }
}

@Composable
fun SegIcon(kind: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(58.dp, 38.dp).clip(RoundedCornerShape(22.dp)).background(if (on) Sb.card else Color.Transparent).tap(kind, onClick), Alignment.Center) {
        Ic(kind, 22.dp, if (on) Sb.ink else Sb.mute)
    }
}

@Composable
private fun TileBox(name: String, count: Int, tab: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(Sb.card).tap(name, onClick)) {
        Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp).width(32.dp).height(8.dp)
            .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(tab))
        Text("$count", fontSize = 12.sp, color = Sb.mute, modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 22.dp))
        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp, end = 4.dp))
    }
}

@Composable
private fun Meta(n: Item, hasPreview: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fmtDate(n.savedAt, hasPreview), fontSize = 13.sp, color = Sb.mute)
        if (n.pinned) { Spacer(Modifier.width(6.dp)); Ic("star", 14.dp, Color(0xFFFFB400)) }
    }
}

@Composable
fun NoteRow(n: Item, nav: NavController) {
    val imgs = n.allImages(); val preview = plainPreview(n.body)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Sb.card).tap("Open ${n.title}") { nav.navigate("item/${n.id}") }.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (preview.isBlank()) { Meta(n, false); Text(n.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 19.sp) }
            else {
                Text(n.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 19.sp, maxLines = 1)
                Meta(n, true)
                Text(preview, fontSize = 14.sp, color = Sb.mute, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (imgs.isNotEmpty()) {
            Box(Modifier.padding(start = 12.dp).size(88.dp, 96.dp)) {
                if (imgs.size > 1) ItemImage(n.copy(imageUri = imgs[1]), Modifier.size(58.dp, 74.dp).align(Alignment.CenterStart).rotate(-8f), 10.dp)
                ItemImage(n.copy(imageUri = imgs[0]), Modifier.size(58.dp, 74.dp).align(Alignment.CenterEnd).rotate(6f)
                    .border(2.dp, Color.White, RoundedCornerShape(10.dp)), 10.dp)
            }
        }
    }
}

@Composable
fun NoteTile(n: Item, nav: NavController, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(Sb.card).tap("Open ${n.title}") { nav.navigate("item/${n.id}") }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(n.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 17.sp, maxLines = 1)
        Meta(n, false)
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(140.dp), Alignment.Center) { NotePreviewArt(n) }
    }
}

/** A small drawing of what is inside a note: photos, a checklist, or a page of text. */
@Composable
fun NotePreviewArt(n: Item) {
    val imgs = n.allImages(); val dark = Sb.dark
    val checks = n.body.lines().filter { it.startsWith("- [ ] ") || it.startsWith("- [x] ") }
    val barC = Sb.mute.copy(alpha = .45f); val lineC = Sb.mute.copy(alpha = .22f)
    when {
        imgs.isNotEmpty() -> Box(Modifier.size(120.dp, 130.dp)) {
            if (imgs.size > 1) ItemImage(n.copy(imageUri = imgs[1]), Modifier.size(84.dp, 108.dp).align(Alignment.TopEnd).rotate(7f).border(3.dp, Color.White, RoundedCornerShape(10.dp)), 10.dp)
            ItemImage(n.copy(imageUri = imgs[0]), Modifier.size(84.dp, 108.dp).align(Alignment.BottomStart).rotate(-5f).border(3.dp, Color.White, RoundedCornerShape(10.dp)), 10.dp)
        }
        checks.isNotEmpty() -> {
            val green = if (dark) Color(0xFF26332A) else Color(0xFFE3EEDD); val gd = Color(0xFF5E8F4F)
            Box(Modifier.size(130.dp, 120.dp)) {
                Box(Modifier.align(Alignment.BottomEnd).size(110.dp, 100.dp).rotate(5f).clip(RoundedCornerShape(16.dp)).background(green.copy(alpha = .6f)))
                Column(Modifier.align(Alignment.TopStart).size(112.dp, 104.dp).clip(RoundedCornerShape(16.dp)).background(green).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    checks.take(4).forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(13.dp).clip(RoundedCornerShape(4.dp)).background(if (l.startsWith("- [x]")) gd else Color.Transparent).border(1.5.dp, gd, RoundedCornerShape(4.dp)))
                            Box(Modifier.height(4.dp).weight(1f).clip(RoundedCornerShape(2.dp)).background(gd.copy(alpha = .45f)))
                        }
                    }
                }
            }
        }
        else -> Box(Modifier.size(118.dp, 132.dp)) {
            Box(Modifier.align(Alignment.TopEnd).size(88.dp, 118.dp).rotate(6f).clip(RoundedCornerShape(8.dp)).background(lineC))
            Column(Modifier.align(Alignment.BottomStart).size(88.dp, 118.dp).shadow(3.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                .background(if (dark) Color(0xFF2A2A2A) else Color.White).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth(.6f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(barC))
                repeat(minOf(6, maxOf(2, n.body.length / 28))) { i -> Box(Modifier.fillMaxWidth(if (i % 3 == 2) .5f else 1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(lineC)) }
            }
        }
    }
}

/** Main page: collection tiles on top, then all notes as a list or grid. */
@Composable
fun HomeScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState(); val shelves by vm.shelves.collectAsState()
    val welcomed by vm.welcomed.collectAsState(); val deleted by vm.deleted.collectAsState()
    val snack = remember { SnackbarHostState() }
    var style by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }; var byTitle by rememberSaveable { mutableStateOf(false) }; var asc by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deleted) {
        if (deleted != null) { val r = snack.showSnackbar("Note deleted", "Undo"); if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted() }
    }
    val sorted = remember(all, byTitle, asc) {
        val base = if (byTitle) all.sortedBy { it.title.lowercase() } else all.sortedBy { it.savedAt }
        (if (asc) base else base.reversed()).sortedByDescending { it.pinned }
    }
    val inbox = all.count { it.collection == "Inbox" }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = t.paper, snackbarHost = { SnackbarHost(snack) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TopBtn("menu", "Settings") { style = true }
                            Text("scrapbook", fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 24.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            TopBtn("newfile", "New note") { nav.navigate("edit/0") }
                            TopBtn("search", "Search") { nav.navigate("search") }
                            Box {
                                TopBtn("more", "More") { menu = true }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Rediscover") }, onClick = { menu = false; nav.navigate("rediscover") })
                                    DropdownMenuItem(text = { Text("Collections") }, onClick = { menu = false; nav.navigate("collections") })
                                    DropdownMenuItem(text = { Text("App style and backup") }, onClick = { menu = false; style = true })
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.tap("Collections") { nav.navigate("collections") }) { Ic("folder", 28.dp, Sb.mute) }
                            Ic("chevron", 24.dp, Sb.mute)
                            Text("All notes", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        }
                    }
                    item {
                        data class Tile(val name: String, val count: Int, val tab: Color)
                        val tiles = shelves.map { Tile(it.name, all.count { n -> n.collection == it.name }, TAB_COLORS[it.color % TAB_COLORS.size]) } + Tile("Inbox", inbox, TAB_GRAY)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            tiles.chunked(4).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { tl -> TileBox(tl.name, tl.count, tl.tab, Modifier.weight(1f)) { nav.navigate("shelf/${tl.name}") } }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            if (shelves.isEmpty()) Text("＋ Create your first collection", fontSize = 13.sp, color = t.acc, modifier = Modifier.link("Create a collection") { nav.navigate("collections") })
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(Modifier.clip(RoundedCornerShape(26.dp)).background(Sb.line.copy(alpha = .6f)).padding(4.dp)) {
                                SegIcon("list", !grid) { grid = false }; SegIcon("grid", grid) { grid = true }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ic("sort", 22.dp, Sb.mute)
                                Text(if (byTitle) "Title" else "Date edited", color = Sb.mute, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp).tap("Change sort order type") { byTitle = !byTitle })
                                Box(Modifier.width(1.5.dp).height(22.dp).background(Sb.ink))
                                Box(Modifier.size(44.dp).tap("Reverse order") { asc = !asc }, Alignment.Center) { Ic(if (asc) "up" else "down", 22.dp, Sb.mute) }
                            }
                        }
                    }
                    if (sorted.isEmpty()) item { Text("No notes yet. Tap the pen to write your first one.", color = Sb.mute, fontSize = 15.sp, modifier = Modifier.padding(vertical = 40.dp)) }
                    else if (grid) items(sorted.chunked(2)) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { NoteTile(it, nav, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    } else items(sorted, key = { it.id }) { NoteRow(it, nav) }
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(68.dp).shadow(10.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                    .background(Sb.card).tap("New note") { nav.navigate("edit/0") }, Alignment.Center) { Ic("edit", 30.dp, t.acc) }
            }
        }
        if (style) AppStyleSheet(vm) { style = false }
        if (!welcomed) Welcome({ vm.dismissWelcome() }, { vm.addExamples() })
    }
}
