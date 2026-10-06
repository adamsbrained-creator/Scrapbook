package app.scrapbook.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*

/** Opened when another app shares text, a link or images to Scrapbook. */
@Composable
fun CaptureScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val inc by vm.incoming.collectAsState()
    val src = inc
    if (src == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val hasImage = src.images.isNotEmpty()
    val kind = classifyShare(src.text, hasImage)
    val initial = remember(src) { shareTitle(src.subject, src.text, hasImage) }
    var title by remember(src) { mutableStateOf(initial) }
    var body by remember(src) { mutableStateOf(src.text) }
    var shelf by remember(src) { mutableStateOf("Inbox") }
    var img by remember(src) { mutableStateOf<String?>(null) }
    LaunchedEffect(src) { if (isUrl(src.text) && src.subject.isBlank()) vm.fetchTitle(src.text.trim()) { t2 -> if (title == initial) title = t2 } }
    LaunchedEffect(src) { src.images.firstOrNull()?.let { u -> vm.importImage(u) { p -> img = p } } }
    val ready = !hasImage || img != null

    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Cancel", fontSize = 13.sp, modifier = Modifier.link("Cancel") { vm.clearIncoming() })
                Text(if (ready) "Save to Scrapbook" else "Preparing…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (ready) t.acc else Sb.mute, modifier = Modifier.link("Save") {
                        if (ready) {
                            val color = (System.currentTimeMillis() % 5).toInt()
                            vm.save(Item(title = title.ifBlank { "Untitled" }, body = body, kind = kind, collection = shelf, imageUri = img, color = color))
                            src.images.drop(1).forEach { u ->
                                vm.importImage(u) { p -> vm.save(Item(title = title.ifBlank { "Shared image" }, body = "", kind = Kind.Image, collection = shelf, imageUri = p, color = color)) }
                            }
                            vm.clearIncoming()
                        }
                    })
            }
            Text("Saving a ${kind.name.lowercase()}" + if (src.images.size > 1) " (${src.images.size} images)" else "", fontSize = 10.5.sp, color = Sb.mute)
            Spacer(Modifier.height(6.dp))
            Field(title, { title = it }, "Title", 24.sp, headFont(), FontWeight.SemiBold)
            if (img != null) {
                Spacer(Modifier.height(10.dp))
                ItemImage(Item(title = "", body = "", kind = kind, collection = shelf, imageUri = img), Modifier.fillMaxWidth().height(160.dp))
            }
            Spacer(Modifier.height(10.dp))
            Field(body, { body = it }, "Add a note…", 15.sp, FontFamily.Serif)
            Spacer(Modifier.weight(1f))
            Text("Collection", fontSize = 10.5.sp, color = Sb.mute, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp), Arrangement.spacedBy(5.dp)) {
                Chip("Inbox", shelf == "Inbox") { shelf = "Inbox" }
                vm.shelves.forEach { Chip(it.name, shelf == it.name) { shelf = it.name } }
            }
        }
    }
}
