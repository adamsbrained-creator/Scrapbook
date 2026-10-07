package app.scrapbook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Create (existing == null) or edit a collection: name, description, color, cover. Renaming moves its notes along. */
@Composable
fun ShelfSheet(vm: ArchiveViewModel, existing: Shelf?, onDone: () -> Unit) {
    val all = vm.shelves.value
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var color by remember { mutableIntStateOf(existing?.color ?: 0) }
    var cover by remember { mutableStateOf(existing?.coverUri) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) vm.importImage(u) { cover = it } }
    val clean = name.trim()
    val clash = all.any { it.name.equals(clean, true) && it.name != existing?.name } || clean.equals("Inbox", true) || clean.equals("All", true)
    val ok = clean.isNotEmpty() && !clash
    Sheet(if (existing == null) "New collection" else "Edit collection", onDone, doneLabel = "Close") {
        Field(name, { name = it.filterNot { c -> c in "/?#{}" } }, "Name", 18.sp, headFont(), FontWeight.SemiBold)
        Field(desc, { desc = it }, "Description (optional)", 13.sp, FontFamily.SansSerif)
        if (clash) Text("That name is already used.", fontSize = 11.sp, color = Color(0xFFB3261E))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sb.notes.forEachIndexed { i, c ->
                Box(Modifier.size(26.dp).clip(CircleShape).background(c).border(if (i == color % 5) 2.dp else 1.dp, if (i == color % 5) Sb.ink else Sb.line, CircleShape)
                    .tap(COLOR_NAMES[i]) { color = i })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Chip(if (cover == null) "Choose cover" else "Change cover") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (cover != null) Chip("Remove cover") { cover = null }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Chip("Save", ok) { if (ok) { vm.saveShelf(Shelf(clean, desc.trim(), color, cover), existing?.name); onDone() } }
            if (existing != null) Chip("Delete (notes move to Inbox)") { vm.deleteShelf(existing.name); onDone() }
        }
    }
}

/** Move a note into another collection. */
@Composable
fun MoveSheet(item: Item, vm: ArchiveViewModel, onDone: () -> Unit) = Sheet("Add to collection", onDone) {
    val shelves by vm.shelves.collectAsState()
    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(5.dp)) {
        Chip("Inbox", item.collection == "Inbox") { vm.save(item.copy(collection = "Inbox")); onDone() }
        shelves.forEach { s -> Chip(s.name, item.collection == s.name) { vm.save(item.copy(collection = s.name)); onDone() } }
    }
    if (shelves.isEmpty()) Text("No collections yet. Create one from the Collections screen.", fontSize = 12.sp, color = Sb.mute)
}

/** Edit a note's tags. */
@Composable
fun TagSheet(item: Item, vm: ArchiveViewModel, onDone: () -> Unit) {
    var text by remember { mutableStateOf(item.tags.joinToString(", ")) }
    Sheet("Tags", { vm.save(item.copy(tags = text.split(",").map { it.trim() }.filter { it.isNotEmpty() })); onDone() }, doneLabel = "Save") {
        Field(text, { text = it }, "Tags, separated by commas", 14.sp, FontFamily.SansSerif)
        Text("Tags are searchable.", fontSize = 11.sp, color = Sb.mute)
    }
}

@Composable
fun CollectionsScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState(); val shelves by vm.shelves.collectAsState()
    var edit by remember { mutableStateOf<Shelf?>(null) }; var creating by remember { mutableStateOf(false) }
    Scaffold(containerColor = t.paper) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 15.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("‹", fontSize = 22.sp, modifier = Modifier.link("Go back") { nav.popBackStack() })
                    Text("Collections", fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                    Chip("＋ New") { creating = true }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip("All notes · ${all.size}") { nav.navigate("shelf/All") }
                    Chip("Inbox · ${all.count { it.collection == "Inbox" }}") { nav.navigate("shelf/Inbox") }
                }
                if (shelves.isEmpty()) Text("No collections yet. Make one for books, trips or ideas, then file notes into it.", fontSize = 13.sp, color = Sb.mute, modifier = Modifier.padding(vertical = 20.dp))
                shelves.forEach { s ->
                    val its = all.filter { it.collection == s.name }
                    Box(Modifier.fillMaxWidth().heightIn(min = 124.dp).clip(RoundedCornerShape(t.radius)).background(Sb.card)
                        .border(1.dp, Sb.line, RoundedCornerShape(t.radius)).tap { nav.navigate("shelf/${s.name}") }) {
                        Row(Modifier.padding(11.dp), Arrangement.spacedBy(10.dp)) {
                            ItemImage(coverOf(s), Modifier.size(40.dp), 10.dp)
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(itemsLabel(its.size) + if (s.description.isNotBlank()) " · " + s.description else "", fontSize = 10.5.sp, color = Sb.mute, maxLines = 1)
                            }
                            Text("Edit", fontSize = 12.sp, color = t.acc, modifier = Modifier.link("Edit ${s.name}") { edit = s })
                        }
                        Row(Modifier.align(Alignment.BottomStart).padding(start = 11.dp).wrapContentWidth(Alignment.Start, unbounded = true), Arrangement.spacedBy(6.dp)) {
                            its.take(3).forEach {
                                Text(it.title, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                                    modifier = Modifier.size(96.dp, 46.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Sb.notes[it.color % 5]).padding(8.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (creating) ShelfSheet(vm, null) { creating = false }
            edit?.let { ShelfSheet(vm, it) { edit = null } }
        }
    }
}
