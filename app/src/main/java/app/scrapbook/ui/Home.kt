package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import kotlin.math.abs
import kotlin.math.roundToInt
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TAB_COLORS = listOf(Color(0xFF3DD9D6), Color(0xFFFF5A44), Color(0xFFFFC107), Color(0xFF00A97F), Color(0xFF8E7CF0), Color(0xFF3B82F6), Color(0xFFFF6FAE), Color(0xFFFF8A3D))
val TAB_NAMES = listOf("Teal", "Red", "Amber", "Green", "Violet", "Blue", "Pink", "Orange")
val TAB_GRAY = Color(0xFFB4B4B4)

internal fun fmtDate(ms: Long, long: Boolean): String {
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
    Box(Modifier.size(58.dp, 38.dp).clip(RoundedCornerShape(22.dp)).background(if (on) Sb.raised else Color.Transparent).tap(kind, onClick), Alignment.Center) {
        Ic(kind, 22.dp, if (on) Sb.ink else Sb.mute)
    }
}

/** A box tile for the two square looks: a card with a colored tab (default), or a card with two sheets peeking out behind it. The other Box styles are in BoxStyles.kt. */
@Composable
fun TileBox(name: String, count: Int, tab: Color, modifier: Modifier, onHold: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    if (LocalBoxStyle.current == 1) {
        Box(modifier.aspectRatio(1f)) {
            Box(Modifier.matchParentSize().padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 6.dp).rotate(-4f).clip(shape).background(tab.copy(alpha = .45f)))
            Box(Modifier.matchParentSize().padding(start = 2.dp, end = 6.dp, top = 6.dp, bottom = 2.dp).rotate(3f).clip(shape).background(Sb.line))
            Box(Modifier.matchParentSize().padding(3.dp).clip(shape).background(Sb.card).tapOrHold(name, "Delete box", onHold, onClick)) {
                Row(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tab)); Spacer(Modifier.width(6.dp))
                    Text("$count", fontSize = 12.sp, color = Sb.mute)
                }
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp, end = 4.dp))
            }
        }
    } else Box(modifier.aspectRatio(1f).clip(shape).background(Sb.card).tapOrHold(name, "Delete box", onHold, onClick)) {
        Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp).width(32.dp).height(8.dp)
            .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(tab))
        Text("$count", fontSize = 12.sp, color = Sb.mute, modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 22.dp))
        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp, end = 4.dp))
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
private fun RowTitle(n: Item, size: TextUnit = 19.sp, lines: Int = 1) {
    Text(n.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = size, maxLines = lines, overflow = TextOverflow.Ellipsis)
}

/** One scrap in the list. It follows the scrap's page layout, so changing the layout changes how it looks here. Hold it to run onHold (delete). */
@Composable
fun NoteRow(n: Item, nav: NavController, onHold: () -> Unit = {}) {
    val imgs = n.allImages(); val preview = if (LocalShowPreview.current) plainPreview(bodyWithoutTitle(n.title, n.body)) else ""
    val shape = RoundedCornerShape(28.dp); val tint = LocalSb.current.tint
    val open = { nav.navigate("edit/${n.id}") }
    val card = Modifier.fillMaxWidth().clip(shape).background(Sb.card).tapOrHold("Open ${n.title}", "Delete scrap", onHold, open).padding(20.dp)
    when (n.layout) {
        Layout.Classic -> Row(card, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (preview.isBlank()) { Meta(n, false); Text(n.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 19.sp) }
                else {
                    RowTitle(n)
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
        Layout.Journal -> Column(card, Arrangement.spacedBy(4.dp)) {
            Meta(n, true)
            RowTitle(n, 24.sp, 2)
            if (preview.isNotBlank()) Text(preview, fontSize = 14.sp, color = Sb.mute, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
            if (n.showMeta) Row(Modifier.padding(top = 8.dp), Arrangement.spacedBy(6.dp)) {
                listOf(n.collection, n.kind.label).forEach { s ->
                    Text(s, fontSize = 11.sp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(tint).padding(horizontal = 9.dp, vertical = 4.dp))
                }
            }
        }
        Layout.Gallery -> Column(card, Arrangement.spacedBy(8.dp)) {
            ItemImage(n.copy(imageUri = imgs.firstOrNull()), Modifier.fillMaxWidth().height(150.dp), 18.dp)
            Text(n.title, Modifier.fillMaxWidth(), fontFamily = headFont(), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = 14.sp, textAlign = TextAlign.Center, color = Sb.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (imgs.size > 1) Text("${imgs.size} photos", Modifier.fillMaxWidth(), fontSize = 11.sp, color = Sb.mute, textAlign = TextAlign.Center)
        }
        Layout.TwoColumn -> Column(card, Arrangement.spacedBy(6.dp)) {
            RowTitle(n)
            val w = preview.split(" ").filter { it.isNotBlank() }; val h = (w.size + 1) / 2
            if (w.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(w.take(h).joinToString(" "), Modifier.weight(1f), fontSize = 13.sp, color = Sb.mute, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(w.drop(h).joinToString(" "), Modifier.weight(1f), fontSize = 13.sp, color = Sb.mute, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        Layout.Quote -> Column(card, Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
            Text("“" + plainPreview(n.body, " ").ifBlank { n.title }.take(140) + "”", fontFamily = headFont(),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 19.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(n.source.ifBlank { n.title }, fontSize = 11.sp, color = Sb.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Layout.Reference -> Column(card, Arrangement.spacedBy(6.dp)) {
            RowTitle(n)
            if (preview.isNotBlank()) Text(preview, fontSize = 14.sp, color = Sb.mute, maxLines = 2)
            if (n.showMeta) Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .border(1.dp, Sb.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp)) {
                listOf("Box" to n.collection, "Kind" to n.kind.label, "Saved" to fmtDate(n.savedAt, true)).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween) {
                        Text(k, fontSize = 11.sp, color = Sb.mute); Text(v, fontSize = 11.5.sp, maxLines = 1)
                    }
                }
            }
        }
        Layout.Collection -> Column(card, Arrangement.spacedBy(6.dp)) {
            ItemImage(n.copy(imageUri = imgs.firstOrNull()), Modifier.fillMaxWidth().height(96.dp), 16.dp)
            RowTitle(n)
            if (preview.isNotBlank()) Text(preview, fontSize = 14.sp, color = Sb.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("In ${n.collection}", fontSize = 11.sp, color = Sb.mute)
        }
        Layout.Blank -> Box(Modifier.fillMaxWidth().height(132.dp).clip(shape).background(Sb.card).tapOrHold("Open ${n.title}", "Delete scrap", onHold, open)) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 16.dp).rotate(-3f).width(130.dp)
                .clip(RoundedCornerShape(8.dp)).background(Sb.notes[n.color % 5]).padding(8.dp)) {
                Text(n.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp).rotate(2f).width(150.dp)
                .clip(RoundedCornerShape(8.dp)).background(tint).padding(8.dp)) {
                Text(preview.ifBlank { n.kind.label }, fontSize = 12.sp, color = Sb.mute, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            ItemImage(n.copy(imageUri = imgs.firstOrNull()), Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 10.dp).size(54.dp, 60.dp).rotate(-4f), 8.dp)
        }
    }
}

@Composable
fun NoteTile(n: Item, nav: NavController, modifier: Modifier, onHold: () -> Unit = {}) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(Sb.card).tapOrHold("Open ${n.title}", "Delete scrap", onHold) { nav.navigate("edit/${n.id}") }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(n.title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Meta(n, false)
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(140.dp), Alignment.Center) { NotePreviewArt(n) }
    }
}

@Composable
private fun PBar(w: Float, h: Int, c: Color) { Box(Modifier.fillMaxWidth(w).height(h.dp).clip(RoundedCornerShape(2.dp)).background(c)) }

/** A small drawing of what is inside a note. Non-Classic layouts draw their own look; Classic shows photos, a checklist, or a page of text. */
@Composable
fun NotePreviewArt(n: Item) {
    val imgs = n.allImages(); val dark = Sb.dark
    val checks = n.body.lines().filter { it.startsWith("- [ ] ") || it.startsWith("- [x] ") }
    val barC = Sb.mute.copy(alpha = .45f); val lineC = Sb.mute.copy(alpha = .22f)
    val paper = if (dark) Color(0xFF2A2A2A) else Color.White; val chipC = LocalSb.current.tint
    when {
        n.layout == Layout.Quote -> Column(Modifier.size(130.dp, 124.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("“", fontFamily = headFont(), fontSize = 44.sp, color = LocalSb.current.acc)
            Text(plainPreview(n.body, " ").ifBlank { n.title }.take(70), fontFamily = headFont(), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        n.layout == Layout.Gallery -> ItemImage(n.copy(imageUri = imgs.firstOrNull()), Modifier.size(132.dp, 124.dp).border(3.dp, Color.White, RoundedCornerShape(14.dp)), 14.dp)
        n.layout == Layout.Collection -> Column(Modifier.size(124.dp, 124.dp), Arrangement.spacedBy(6.dp)) {
            ItemImage(n.copy(imageUri = imgs.firstOrNull()), Modifier.fillMaxWidth().height(70.dp), 12.dp)
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(5.dp)) {
                repeat(3) { i -> Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Sb.notes[(n.color + i) % 5])) }
            }
        }
        n.layout == Layout.Blank -> Box(Modifier.size(132.dp, 124.dp)) {
            Box(Modifier.align(Alignment.TopStart).size(70.dp, 40.dp).rotate(-5f).clip(RoundedCornerShape(6.dp)).background(Sb.notes[n.color % 5]))
            Box(Modifier.align(Alignment.CenterEnd).size(66.dp, 56.dp).rotate(4f).shadow(2.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(paper))
            ItemImage(n.copy(imageUri = imgs.firstOrNull()), Modifier.align(Alignment.BottomStart).size(48.dp, 54.dp).rotate(-4f), 6.dp)
        }
        n.layout == Layout.Journal || n.layout == Layout.TwoColumn || n.layout == Layout.Reference ->
            Column(Modifier.size(108.dp, 124.dp).shadow(3.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(paper).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (n.layout == Layout.Journal) PBar(.35f, 3, lineC)
                PBar(.6f, 5, barC)
                if (n.layout == Layout.TwoColumn) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(5.dp)) {
                        repeat(2) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { repeat(5) { j -> PBar(if (j == 3) .6f else 1f, 3, lineC) } } }
                    }
                } else {
                    repeat(2) { PBar(1f, 3, lineC) }
                    if (n.layout == Layout.Journal) Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                        repeat(3) { Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(chipC)) }
                    } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).border(1.dp, lineC, RoundedCornerShape(5.dp)).padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { PBar(1f, 3, lineC) } }
                }
            }
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

/** Lets the content be flicked left or right to dismiss it. */
@Composable
fun SwipeAway(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    var x by remember { mutableFloatStateOf(0f) }
    Box(Modifier.offset { IntOffset(x.roundToInt(), 0) }.alpha((1f - abs(x) / 900f).coerceIn(0.2f, 1f))
        .pointerInput(Unit) { detectHorizontalDragGestures(onDragEnd = { if (abs(x) > 220f) onDismiss() else x = 0f }) { c, d -> c.consume(); x += d } }) { content() }
}

@Composable
private fun Wordmark(modifier: Modifier, center: Boolean = false, size: TextUnit = 30.sp) {
    Text("scrapbook", modifier, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = size, letterSpacing = (-0.03).em, textAlign = if (center) TextAlign.Center else TextAlign.Start)
}

/** A round white button, used by the "Round" header. */
@Composable
private fun RoundTop(kind: String, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Sb.card).tap(desc, onClick), Alignment.Center) { Ic(kind, 22.dp, Sb.ink) }
}

@Composable
private fun MoreMenu(open: Boolean, onDismiss: () -> Unit, onRediscover: () -> Unit, onCollections: () -> Unit, onSettings: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Rediscover") }, onClick = onRediscover)
        DropdownMenuItem(text = { Text("Boxes") }, onClick = onCollections)
        DropdownMenuItem(text = { Text("Settings") }, onClick = onSettings)
    }
}

/** The header the Home page and the All scraps page share: menu, wordmark and search, laid out the way the Main page header setting says. */
@Composable
internal fun MainHeader(header: Int, hs: Float, nav: NavController, onMenu: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val moreMenu: @Composable () -> Unit = {
        MoreMenu(menu, { menu = false }, { menu = false; nav.navigate("rediscover") }, { menu = false; nav.navigate("collections") }, { menu = false; nav.navigate("settings") })
    }
    when (header) {
        0 -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TopBtn("menu", "Menu", onMenu)
            Wordmark(Modifier.weight(1f).padding(start = 8.dp), size = 30.sp * hs)
            TopBtn("search", "Search") { nav.navigate("search") }
            Box { TopBtn("more", "More") { menu = true }; moreMenu() }
        }
        1 -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) { TopBtn("menu", "Menu", onMenu) }
            Wordmark(Modifier, size = 30.sp * hs)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                TopBtn("search", "Search") { nav.navigate("search") }
                Box { TopBtn("more", "More") { menu = true }; moreMenu() }
            }
        }
        else -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundTop("menu", "Menu", onMenu)
            Wordmark(Modifier.weight(1f), center = true, size = 34.sp * hs)
            RoundTop("search", "Search") { nav.navigate("search") }
        }
    }
}

/** After a scrap is deleted: back to the All scraps page if it is open underneath, otherwise to the Home page. */
fun NavController.backToLists() { if (!popBackStack("scraps", false)) popBackStack("home", false) }

/** A group of menu rows on one rounded card, the same grouping the Settings page uses. */
@Composable
private fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Sb.line.copy(alpha = .5f)), content = content)
}

/** A row in a menu group: icon, label, chevron. The last row of a group has no divider under it. */
@Composable
private fun SheetRow(icon: String, label: String, last: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().height(54.dp).tap(label, onClick).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(icon, 24.dp, Sb.ink); Spacer(Modifier.width(14.dp))
            Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Ic("chevron", 18.dp, Sb.mute)
        }
        if (!last) Box(Modifier.padding(start = 56.dp, end = 18.dp).fillMaxWidth().height(1.dp).background(Sb.line))
    }
}

/**
 * The menu: the places to browse, then Settings and About, plus a big New scrap button. Opened from the menu button in the header or the bottom bar.
 * App style, backup and box management live on the Settings page now, so they are not repeated here.
 */
@Composable
fun MenuSheet(nav: NavController, onClose: () -> Unit, onNew: () -> Unit) {
    fun go(route: String) { onClose(); nav.navigate(route) }
    Sheet("scrapbook", onClose, doneLabel = "New scrap", primary = { onClose(); onNew(); nav.navigate("edit/0") }) {
        MenuGroup {
            SheetRow("home", "Home") { onClose(); nav.popBackStack("home", false) }
            SheetRow("list", "All scraps") { go("scraps") }
            SheetRow("star", "Pinned") { go("shelf/Pinned") }
            SheetRow("clock", "Recently edited", last = true) { go("shelf/Recent") }
        }
        MenuGroup {
            SheetRow("sparkle", "Rediscover") { go("rediscover") }
            SheetRow("search", "Search", last = true) { go("search") }
        }
        MenuGroup {
            SheetRow("sliders", "Settings") { go("settings") }
            SheetRow("info", "About", last = true) { go("about") }
        }
    }
}

/** The "New scrap" pill, one add button per page. */
@Composable
fun NewScrapPill(modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.padding(20.dp).shadow(10.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(Sb.ink)
        .tap("New scrap", onClick).padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Ic("edit", 24.dp, Sb.card); Text("New scrap", color = Sb.card, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    }
}

/**
 * Bottom bar option, copied from Binder: one round menu button. Tap it for the menu. Hold it and the round + button pops out beside it
 * (tap + to add a scrap). Tap anywhere else, or the menu button again, to put the + away. Place it with Modifier.matchParentSize() inside a Box.
 */
@Composable
fun BottomNav(modifier: Modifier, onMenu: () -> Unit, onAdd: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val pop by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = .6f, stiffness = Spring.StiffnessMedium), label = "addPop")
    val ring by animateDpAsState(if (open) 4.dp else 0.dp, tween(150), label = "menuRing")
    val press by animateFloatAsState(if (open) .94f else 1f, tween(150), label = "menuPress")
    val ringColor = Sb.line
    Box(modifier) {
        // While the + is out, a tap anywhere else only puts it away.
        if (open) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { open = false } })
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp)) {
            Box(Modifier.align(Alignment.BottomEnd).offset(x = (-68).dp).size(58.dp)
                .graphicsLayer { alpha = pop.coerceIn(0f, 1f); scaleX = .4f + .6f * pop; scaleY = .4f + .6f * pop; translationX = (1f - pop) * 36.dp.toPx() }
                .clip(CircleShape).background(Sb.ink).border(1.dp, Sb.line, CircleShape)
                .then(if (open) Modifier.tap("Add scrap") { open = false; onAdd() } else Modifier), Alignment.Center) { Ic("plus", 28.dp, Sb.card) }
            Box(Modifier.align(Alignment.BottomEnd).size(58.dp)
                .graphicsLayer { scaleX = press; scaleY = press }
                .drawBehind { if (ring > 0.dp) drawCircle(ringColor, radius = size.minDimension / 2 + ring.toPx()) }
                .clip(CircleShape).background(Sb.ink)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { open = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onTap = { open = false; onMenu() })
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = "Menu"; role = Role.Button
                    onClick("Open menu") { open = false; onMenu(); true }
                    onLongClick("Add scrap") { open = false; onAdd(); true }
                }, Alignment.Center) { Ic("menu", 26.dp, Sb.card) }
        }
    }
}

/** The All scraps page: collection tiles on top, then every scrap as a list or grid. Opened from the Home page and the menu. */
@Composable
fun AllScrapsScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState(); val shelves by vm.shelves.collectAsState()
    val inboxColor by vm.inboxColor.collectAsState(); val deleted by vm.deleted.collectAsState(); val header by vm.headerStyle.collectAsState()
    val snack = remember { SnackbarHostState() }
    val inboxHidden by vm.inboxHidden.collectAsState(); var deleting by remember { mutableStateOf<String?>(null) }; var deletingScrap by remember { mutableStateOf<Item?>(null) }
    var menuSheet by remember { mutableStateOf(false) }; val navStyle by vm.navStyle.collectAsState()
    // List or grid starts as the Default view setting. Tapping the switch here only changes it until you leave the app.
    val defaultGrid by vm.defaultGrid.collectAsState(); var gridTouched by rememberSaveable { mutableStateOf(false) }; var gridPick by rememberSaveable { mutableStateOf(false) }
    val grid = if (gridTouched) gridPick else defaultGrid
    // Sorting is saved, so the Sort by and Sort order settings and the sort controls on this page always agree.
    val byTitle by vm.sortTitle.collectAsState(); val asc by vm.sortAsc.collectAsState()
    val showBoxes by vm.showBoxes.collectAsState(); val headerSize by vm.headerSize.collectAsState()
    val hs = listOf(.8f, 1f, 1.2f)[headerSize.coerceIn(0, 2)]
    LaunchedEffect(deleted) {
        if (deleted != null) { val r = snack.showSnackbar("Scrap deleted", "Undo", duration = SnackbarDuration.Long); if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted() }
    }
    val sorted = remember(all, byTitle, asc) {
        val base = if (byTitle) all.sortedBy { it.title.lowercase() } else all.sortedBy { it.savedAt }
        (if (asc) base else base.reversed()).sortedByDescending { it.pinned }
    }
    val inbox = all.count { it.collection == "Inbox" }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = t.paper, snackbarHost = { SnackbarHost(snack) { d -> SwipeAway({ d.dismiss() }) { Snackbar(d, shape = RoundedCornerShape(20.dp), containerColor = Sb.ink, contentColor = Sb.card, actionColor = t.acc) } } }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { MainHeader(header, hs, nav) { menuSheet = true } }
                    // The page's title sits small under the menu button, the way the other pages (Settings and the rest) show theirs.
                    item { Text("All scraps", Modifier.padding(start = 12.dp).offset(y = (-4).dp), fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    if (showBoxes) item {
                        val inboxTile = if (inboxHidden) emptyList<BoxTile>() else listOf(BoxTile("Inbox", inbox, if (inboxColor < 0) TAB_GRAY else TAB_COLORS[inboxColor % TAB_COLORS.size]))
                        val tiles = shelves.topLevel().map { BoxTile(it.name, all.count { n -> n.collection == it.name }, TAB_COLORS[it.color % TAB_COLORS.size]) } + inboxTile
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            BoxGrid(tiles, onHold = { deleting = it }, onNew = { nav.navigate("collections") }) { nav.navigate("shelf/$it") }
                            if (shelves.isEmpty() && LocalBoxStyle.current != 4 && LocalBoxStyle.current != 6) Text("＋ Create your first box", fontSize = 13.sp, color = t.acc, modifier = Modifier.link("Create a box") { nav.navigate("collections") })
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(Modifier.clip(RoundedCornerShape(26.dp)).background(Sb.line.copy(alpha = .6f)).padding(4.dp)) {
                                SegIcon("list", !grid) { gridTouched = true; gridPick = false }; SegIcon("grid", grid) { gridTouched = true; gridPick = true }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ic("sort", 22.dp, Sb.mute)
                                // Switching the sort field also resets the order to its natural one: newest first, or A to Z.
                                Text(if (byTitle) "Title" else "Date edited", color = Sb.mute, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp).tap("Change sort order type") { vm.setSort(!byTitle, !byTitle) })
                                Box(Modifier.width(1.5.dp).height(22.dp).background(Sb.ink))
                                Box(Modifier.size(44.dp).tap("Reverse order") { vm.setSortAsc(!asc) }, Alignment.Center) { Ic(if (asc) "up" else "down", 22.dp, Sb.mute) }
                            }
                        }
                    }
                    if (sorted.isEmpty()) item { Text(if (navStyle == 1) "No scraps yet. Hold the menu button to add your first one." else "No scraps yet. Tap New scrap to write your first one.", color = Sb.mute, fontSize = 15.sp, modifier = Modifier.padding(vertical = 40.dp)) }
                    else if (grid) items(sorted.chunked(2)) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { n -> NoteTile(n, nav, Modifier.weight(1f)) { deletingScrap = n } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    } else items(sorted, key = { it.id }) { n -> NoteRow(n, nav) { deletingScrap = n } }
                }
                val addScrap = { vm.pendingShelf = null; nav.navigate("edit/0") }
                if (navStyle == 1) BottomNav(Modifier.matchParentSize(), { menuSheet = true }, addScrap)
                else NewScrapPill(Modifier.align(Alignment.BottomEnd), addScrap)
            }
        }
        if (menuSheet) MenuSheet(nav, { menuSheet = false }) { vm.pendingShelf = null }
        deleting?.let { n -> DeleteBoxSheet(n, vm, { deleting = null }) }
        deletingScrap?.let { n -> DeleteScrapSheet(n, vm) { deletingScrap = null } }
    }
}
