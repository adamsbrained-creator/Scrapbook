package app.scrapbook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*

private val COLOR_NAMES = listOf("Butter", "Sage", "Blue", "Rose", "Lilac")
private fun coverOf(s: Shelf) = Item(title = "", body = "", kind = Kind.Image, collection = "", imageUri = s.coverUri, color = s.color)

/** Create (existing == null) or edit a collection: name, description, color, cover. Renaming moves its notes along. A new box goes inside `parent` ("" = main page). */
@Composable
fun ShelfSheet(vm: ArchiveViewModel, existing: Shelf?, onDone: () -> Unit, parent: String = "") {
    val all = vm.shelves.value
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var color by remember { mutableIntStateOf(existing?.color ?: 0) }
    var cover by remember { mutableStateOf(existing?.coverUri) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) vm.importImage(u) { cover = it } }
    val clean = name.trim()
    val clash = all.any { it.name.equals(clean, true) && it.name != existing?.name } || clean.equals("Inbox", true) || clean.equals("All", true)
    val ok = clean.isNotEmpty() && !clash
    Sheet(if (existing != null) "Edit box" else if (parent.isNotBlank()) "New box in \"$parent\"" else "New box", onDone, doneLabel = if (existing == null) "Create box" else "Save", enabled = ok,
        primary = { if (ok) { vm.saveShelf(Shelf(clean, desc.trim(), color, cover, existing?.parent ?: parent), existing?.name); onDone() } }) {
        InputBox { Field(name, { name = it.filterNot { c -> c in "/?#{}" } }, "Name", 18.sp, headFont(), FontWeight.Medium) }
        InputBox { Field(desc, { desc = it }, "Description (optional)", 15.sp, headFont()) }
        if (clash) Text("That name is already used.", fontSize = 11.sp, color = Color(0xFFB3261E))
        BoxColorDots(color) { color = it }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Chip(if (cover == null) "Choose cover" else "Change cover") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (cover != null) Chip("Remove cover") { cover = null }
        }
        if (existing != null) Row { Chip("Delete box (scraps move to Inbox)") { vm.deleteShelf(existing.name); onDone() } }
    }
}

/**
 * Confirm deleting a box (opened by long-pressing it, or from the box's menu). Deleting never loses scraps unless you pick
 * the second option and tap twice. Inbox is built in: removing it hides the box, and its scraps stay in All scraps.
 */
@Composable
fun DeleteBoxSheet(name: String, vm: ArchiveViewModel, onDone: () -> Unit, afterDelete: () -> Unit = {}) {
    val all by vm.items.collectAsState(); val shelves by vm.shelves.collectAsState()
    val inside = if (name == "Inbox") emptyList() else shelves.descendantsOf(name)
    val tree = setOf(name) + inside.map { it.name }
    val count = all.count { it.collection in tree }; val inbox = name == "Inbox"
    val scraps = (if (count == 1) "1 scrap" else "$count scraps") + (if (inside.isEmpty()) "" else if (inside.size == 1) " and 1 box inside" else " and ${inside.size} boxes inside")
    var sure by remember { mutableStateOf(false) }
    fun finish() { onDone(); afterDelete() }
    Sheet(if (inbox) "Remove Inbox?" else "Delete \"$name\"?", onDone, doneLabel = "Cancel") {
        Text(if (inbox) "Inbox is where scraps land when you don't file them. Removing it hides the box. Your scraps stay in All scraps, and you can bring Inbox back from the Boxes screen."
            else "Your scraps are not lost: they move to Inbox." + (if (inside.isEmpty()) "" else " Boxes inside it move up one level."), fontSize = 13.sp, color = Sb.mute)
        Row { Chip(if (inbox) "Remove Inbox, keep scraps" else "Delete box, keep scraps") { if (inbox) vm.removeInbox(false) else vm.deleteShelf(name); finish() } }
        if (count > 0 || inside.isNotEmpty()) {
            if (sure) Text("This permanently deletes $scraps and cannot be undone.", fontSize = 12.sp, color = Color(0xFFB3261E))
            Row { Chip(if (sure) "Yes, delete the box and $scraps" else "Delete the box and its $scraps…") {
                if (!sure) sure = true else { if (inbox) vm.removeInbox(true) else vm.deleteShelfAndScraps(name); finish() }
            } }
        }
    }
}

/** Confirm deleting a scrap (opened by long-pressing it in a list). Right after deleting, an Undo snackbar brings it back. */
@Composable
fun DeleteScrapSheet(item: Item, vm: ArchiveViewModel, onDone: () -> Unit) {
    val title = item.title.ifBlank { "Untitled" }.let { if (it.length > 40) it.take(40) + "…" else it }
    Sheet("Delete \"$title\"?", onDone, doneLabel = "Cancel") {
        Text("The scrap is deleted right away. You can tap Undo straight after to bring it back.", fontSize = 13.sp, color = Sb.mute)
        Row { Chip("Delete scrap") { vm.delete(item); onDone() } }
    }
}

/** Move a note into another collection. */
@Composable
fun MoveSheet(item: Item, vm: ArchiveViewModel, onDone: () -> Unit) = Sheet("Add to box", onDone) {
    val shelves by vm.shelves.collectAsState()
    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(5.dp)) {
        Chip("Inbox", item.collection == "Inbox") { vm.save(item.copy(collection = "Inbox")); onDone() }
        shelves.forEach { s -> Chip(s.name, item.collection == s.name) { vm.save(item.copy(collection = s.name)); onDone() } }
    }
    if (shelves.isEmpty()) Text("No boxes yet. Create one from the Boxes screen.", fontSize = 12.sp, color = Sb.mute)
}

/** Edit a note's tags. */
@Composable
fun TagSheet(item: Item, vm: ArchiveViewModel, onDone: () -> Unit) {
    var text by remember { mutableStateOf(item.tags.joinToString(", ")) }
    Sheet("Tags", { vm.save(item.copy(tags = text.split(",").map { it.trim() }.filter { it.isNotEmpty() })); onDone() }, doneLabel = "Save") {
        InputBox { Field(text, { text = it }, "Tags, separated by commas", 16.sp, headFont()) }
        Text("Tags are searchable.", fontSize = 11.sp, color = Sb.mute)
    }
}

/** The box colors as tappable dots. With includeGray the first dot is gray (-1), used for Inbox. */
@Composable
fun BoxColorDots(selected: Int, includeGray: Boolean = false, onPick: (Int) -> Unit) {
    val ids = (if (includeGray) listOf(-1) else emptyList()) + TAB_COLORS.indices.toList()
    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(10.dp)) {
        ids.forEach { i ->
            val c = if (i < 0) TAB_GRAY else TAB_COLORS[i]; val on = i == selected
            Box(Modifier.size(32.dp).clip(CircleShape).background(c).border(if (on) 3.dp else 1.dp, if (on) Sb.ink else Sb.line, CircleShape)
                .tap(if (i < 0) "Gray" else TAB_NAMES[i]) { onPick(i) })
        }
    }
}

/** Pick a color for the Inbox box. */
@Composable
fun InboxSheet(vm: ArchiveViewModel, onDone: () -> Unit) {
    val sel by vm.inboxColor.collectAsState()
    Sheet("Inbox color", onDone) {
        Text("Shown on the Inbox tile.", fontSize = 13.sp, color = Sb.mute)
        BoxColorDots(sel, includeGray = true) { vm.setInboxColor(it) }
    }
}

@Composable
private fun ShelfCard(s: Shelf, count: Int, modifier: Modifier, nav: NavController, onHold: () -> Unit, onEdit: () -> Unit) {
    Box(modifier.heightIn(min = 176.dp).clip(RoundedCornerShape(28.dp)).background(Sb.card)
        .tapOrHold("Open ${s.name}", "Delete box", onHold) { nav.navigate("shelf/${s.name}") }) {
        Box(Modifier.align(Alignment.TopEnd).padding(end = 22.dp).width(34.dp).height(9.dp)
            .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(TAB_COLORS[s.color % TAB_COLORS.size]))
        Column(Modifier.padding(18.dp).fillMaxWidth()) {
            Text(s.name, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 19.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            Text(if (count == 1) "1 scrap" else "$count scraps", fontSize = 13.sp, color = Sb.mute)
            if (s.parent.isNotBlank()) Text("in ${s.parent}", fontSize = 12.sp, color = Sb.mute, maxLines = 1)
            if (s.description.isNotBlank()) Text(s.description, fontSize = 12.sp, color = Sb.mute, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
                Text("Edit", fontSize = 13.sp, color = LocalSb.current.acc, modifier = Modifier.link("Edit ${s.name}") { onEdit() })
                ItemImage(coverOf(s), Modifier.size(54.dp, 66.dp).rotate(6f).border(2.dp, Color.White, RoundedCornerShape(10.dp)), 10.dp)
            }
        }
    }
}

/** All collections as cards with a colored tab, note count and cover. */
@Composable
fun CollectionsScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState(); val shelves by vm.shelves.collectAsState()
    var edit by remember { mutableStateOf<Shelf?>(null) }; var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }; val inboxHidden by vm.inboxHidden.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = t.paper) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).tap("Go back") { nav.popBackStack() }, Alignment.Center) { Ic("back", 28.dp, Sb.ink) }
                        Text("scrapbook", fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Box(Modifier.size(48.dp).tap("New box") { creating = true }, Alignment.Center) { Ic("plus", 26.dp, Sb.ink) }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Boxes", fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 36.sp)
                        Text("  ${shelves.size}", fontSize = 14.sp, color = Sb.mute, modifier = Modifier.padding(bottom = 7.dp))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("All scraps · ${all.size}") { nav.navigate("shelf/All") }
                        if (inboxHidden) Chip("Show Inbox") { vm.setInboxHidden(false) }
                        else Chip("Inbox · ${all.count { it.collection == "Inbox" }}") { nav.navigate("shelf/Inbox") }
                    }
                }
                if (shelves.isEmpty()) item {
                    Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No boxes yet. Make one for books, trips or ideas, then file scraps into it.", fontSize = 15.sp, color = Sb.mute)
                        Chip("＋ New box", true) { creating = true }
                    }
                }
                items(shelves.chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { s -> ShelfCard(s, all.count { it.collection == s.name }, Modifier.weight(1f), nav, { deleting = s.name }) { edit = s } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (creating) ShelfSheet(vm, null) { creating = false }
        edit?.let { ShelfSheet(vm, it) { edit = null } }
        deleting?.let { n -> DeleteBoxSheet(n, vm, { deleting = null }) }
    }
}
