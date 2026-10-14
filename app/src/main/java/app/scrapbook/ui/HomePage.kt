package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// The Home page, the page the app starts on: a greeting, a quick way to write something down, the scrap you were last in,
// your boxes, your pinned scraps and the latest ones. The full list lives on the All scraps page (see Home.kt).

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }

/** "5 min ago", "2 h ago", "yesterday", then the date. */
private fun ago(ms: Long): String {
    val mins = ((System.currentTimeMillis() - ms) / 60_000).coerceAtLeast(0)
    val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        day.format(Date(ms)) == day.format(Date()) -> "${mins / 60} h ago"
        day.format(Date(ms)) == day.format(Date(System.currentTimeMillis() - 86_400_000L)) -> "yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

/** A section title with a small grey link on the right. */
@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = headFont())
        Text(action, Modifier.tap("$title: $action", onClick).padding(vertical = 6.dp), fontSize = 14.sp, color = Sb.mute)
    }
}

/** A small round button in the capture card. */
@Composable
private fun MiniBtn(icon: String, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Sb.paper).tap(desc, onClick), Alignment.Center) { Ic(icon, 20.dp, Sb.ink) }
}

/** Write something down right away: tap the line to start a blank scrap, or use a shortcut (checklist, photo, link). */
@Composable
private fun CaptureCard(onOpen: () -> Unit, onChecklist: () -> Unit, onImage: () -> Unit, onLink: () -> Unit) {
    val acc = LocalSb.current.acc
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Sb.card).padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Jot something down…", Modifier.fillMaxWidth().tap("Write a new scrap", onOpen), fontSize = 17.sp, color = Sb.mute)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniBtn("check", "New checklist", onChecklist); MiniBtn("image", "New scrap with a photo", onImage); MiniBtn("link", "New scrap with a link", onLink)
            }
            Row(Modifier.height(44.dp).clip(CircleShape).background(acc).tap("New scrap", onOpen).padding(start = 16.dp, end = 20.dp),
                Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                Ic("plus", 18.dp, Color(0xFF0B0B0B)); Text("New scrap", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0B0B0B))
            }
        }
    }
}

/** The scrap you edited last, one tap from picking up where you left off. */
@Composable
private fun ContinueCard(n: Item, dot: Color, onOpen: () -> Unit) {
    val acc = LocalSb.current.acc
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Sb.card).tap("Continue ${n.title}", onOpen).padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        Arrangement.spacedBy(14.dp), Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("CONTINUE", fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.8.sp, color = acc)
            Text(n.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Text("${n.collection} · edited ${ago(n.savedAt)}", fontSize = 14.sp, color = Sb.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(Sb.ink), Alignment.Center) { Ic("arrow", 22.dp, Sb.paper) }
    }
}

@Composable
private fun HomeChip(b: BoxTile, onClick: () -> Unit) {
    Row(Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(Sb.card).tap("Open ${b.name}, ${b.count}", onClick).padding(horizontal = 20.dp),
        Arrangement.spacedBy(10.dp), Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(b.tab))
        Text(b.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        Text("${b.count}", fontSize = 15.sp, color = Sb.mute)
    }
}

@Composable
private fun NewBoxChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(Modifier.height(48.dp).clip(shape).dashedOutline(Sb.mute.copy(alpha = .5f), shape).tap("New box", onClick).padding(horizontal = 18.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        Ic("plus", 18.dp, Sb.mute); Text("New box", fontSize = 16.sp, color = Sb.mute)
    }
}

@Composable
private fun PinnedCard(n: Item, preview: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(176.dp).height(136.dp).clip(RoundedCornerShape(28.dp)).background(Sb.card).tap("Open ${n.title}", onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.title, Modifier.weight(1f), fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp)); Ic("star", 14.dp, Color(0xFFFFB400))
        }
        Text(fmtDate(n.savedAt, true), fontSize = 12.sp, color = Sb.mute)
        if (preview) Text(plainPreview(bodyWithoutTitle(n.title, n.body)), Modifier.padding(top = 6.dp), fontSize = 13.sp, lineHeight = 18.sp, color = Sb.mute, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** The Home page: where the app starts. */
@Composable
fun HomeScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState(); val shelves by vm.shelves.collectAsState()
    val inboxColor by vm.inboxColor.collectAsState(); val inboxHidden by vm.inboxHidden.collectAsState()
    val welcomed by vm.welcomed.collectAsState(); val deleted by vm.deleted.collectAsState(); val header by vm.headerStyle.collectAsState()
    val navStyle by vm.navStyle.collectAsState(); val showBoxes by vm.showBoxes.collectAsState(); val headerSize by vm.headerSize.collectAsState()
    val hs = listOf(.8f, 1f, 1.2f)[headerSize.coerceIn(0, 2)]
    val snack = remember { SnackbarHostState() }
    var menuSheet by remember { mutableStateOf(false) }; var deletingScrap by remember { mutableStateOf<Item?>(null) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(deleted) {
        if (deleted != null) { val r = snack.showSnackbar("Scrap deleted", "Undo", duration = SnackbarDuration.Long); if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted() }
    }
    val latest = remember(all) { all.maxByOrNull { it.savedAt } }
    val pinned = remember(all) { all.filter { it.pinned }.sortedByDescending { it.savedAt } }
    val recent = remember(all) { all.sortedByDescending { it.savedAt }.take(4) }
    val showPreview = LocalShowPreview.current
    fun colorOf(box: String): Color =
        if (box == "Inbox") (if (inboxColor < 0) TAB_GRAY else TAB_COLORS[inboxColor % TAB_COLORS.size])
        else shelves.firstOrNull { it.name == box }?.let { TAB_COLORS[it.color % TAB_COLORS.size] } ?: TAB_GRAY
    /** Opens a new scrap, optionally with some text already in it or straight to the photo picker. */
    fun newScrap(body: String? = null, image: Boolean = false) { vm.pendingShelf = null; vm.pendingBody = body; vm.pendingImage = image; nav.navigate("edit/0") }
    fun linkStart(): String { val c = clipboard.getText()?.text?.trim().orEmpty(); return if (Regex("^https?://\\S+$").matches(c)) c else "https://" }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = t.paper, snackbarHost = { SnackbarHost(snack) { d -> SwipeAway({ d.dismiss() }) { Snackbar(d, shape = RoundedCornerShape(20.dp), containerColor = Sb.ink, contentColor = Sb.card, actionColor = t.acc) } } }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { Box(Modifier.padding(horizontal = 16.dp)) { MainHeader(header, hs, nav) { menuSheet = true } } }
                    item {
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), fontSize = 14.sp, color = Sb.mute)
                            Text(greeting(), fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp)
                        }
                    }
                    item { Box(Modifier.padding(horizontal = 20.dp)) { CaptureCard({ newScrap() }, { newScrap("- [ ] ") }, { newScrap(image = true) }, { newScrap(linkStart()) }) } }
                    latest?.let { n -> item { Box(Modifier.padding(horizontal = 20.dp)) { ContinueCard(n, colorOf(n.collection)) { nav.navigate("edit/${n.id}") } } } }
                    if (showBoxes) item {
                        val inbox = all.count { it.collection == "Inbox" }
                        val inboxTile = if (inboxHidden) emptyList<BoxTile>() else listOf(BoxTile("Inbox", inbox, colorOf("Inbox")))
                        val tiles = shelves.topLevel().map { BoxTile(it.name, all.count { n -> n.collection == it.name }, TAB_COLORS[it.color % TAB_COLORS.size]) } + inboxTile
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader("Boxes", "See all") { nav.navigate("collections") }
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(tiles, key = { it.name }) { b -> HomeChip(b) { nav.navigate("shelf/${b.name}") } }
                                item { NewBoxChip { nav.navigate("collections") } }
                            }
                        }
                    }
                    if (pinned.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader("Pinned", "${pinned.size} ${if (pinned.size == 1) "scrap" else "scraps"}") { nav.navigate("shelf/Pinned") }
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(pinned, key = { it.id }) { n -> PinnedCard(n, showPreview) { nav.navigate("edit/${n.id}") } }
                            }
                        }
                    }
                    if (recent.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionHeader("Recent", "All scraps") { nav.navigate("scraps") }
                            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                recent.forEach { n -> NoteRow(n, nav) { deletingScrap = n } }
                            }
                        }
                    } else item { Text("No scraps yet. Write your first one above.", Modifier.padding(horizontal = 24.dp), fontSize = 15.sp, color = Sb.mute) }
                }
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Transparent, t.paper))))
                val addScrap = { newScrap() }
                if (navStyle == 1) BottomNav(Modifier.matchParentSize(), { menuSheet = true }, addScrap)
                else NewScrapPill(Modifier.align(Alignment.BottomEnd), addScrap)
            }
        }
        if (menuSheet) MenuSheet(nav, { menuSheet = false }) { vm.pendingShelf = null }
        deletingScrap?.let { n -> DeleteScrapSheet(n, vm) { deletingScrap = null } }
        if (!welcomed) Welcome({ vm.dismissWelcome() }, { vm.addExamples() })
    }
}
