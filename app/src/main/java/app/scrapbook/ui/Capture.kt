package app.scrapbook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*

/** The box that things shared from Paperback go to. It is made the first time you save one. */
private const val PAPERBACK_BOX = "Paperback"

/** Opened when another app shares text, a link or images to Scrapbook. */
@Composable
fun CaptureScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val inc by vm.incoming.collectAsState()
    val src = inc
    BackHandler(enabled = src != null) { vm.clearIncoming() }
    if (src == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val hasImage = src.images.isNotEmpty()
    val kind = classifyShare(src.text, hasImage)
    val initial = remember(src) { shareTitle(src.subject, src.text, hasImage) }
    var title by remember(src) { mutableStateOf(initial) }
    var body by remember(src) { mutableStateOf(src.text) }
    val fromPaperback = "readest://" in src.text
    var shelf by remember(src) { mutableStateOf(if (fromPaperback) PAPERBACK_BOX else "Inbox") }
    var img by remember(src) { mutableStateOf<String?>(null) }
    LaunchedEffect(src) { if (isUrl(src.text) && src.subject.isBlank()) vm.fetchTitle(src.text.trim()) { t2 -> if (title == initial) title = t2 } }
    LaunchedEffect(src) { src.images.firstOrNull()?.let { u -> vm.importImage(u) { p -> img = p } } }
    val ready = !hasImage || img != null

    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).tap("Cancel") { vm.clearIncoming() }, Alignment.Center) { Text("✕", fontSize = 20.sp, color = Sb.ink) }
                Text("Save to Scrapbook", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 18.sp)
                Text(if (ready) "Save" else "…", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(22.dp)).background(if (ready) t.acc else Sb.mute).tap("Save") {
                        if (ready) {
                            if (shelf == PAPERBACK_BOX && vm.shelves.value.none { it.name == PAPERBACK_BOX }) vm.saveShelf(Shelf(PAPERBACK_BOX, "Quotes and notes from my books.", 0))
                            val color = (System.currentTimeMillis() % 5).toInt() + 1
                            vm.save(Item(title = title.ifBlank { "Untitled" }, body = body, kind = kind, collection = shelf, imageUri = img, color = 0))
                            src.images.drop(1).forEach { u ->
                                vm.importImage(u) { p -> vm.save(Item(title = title.ifBlank { "Shared image" }, body = "", kind = Kind.Image, collection = shelf, imageUri = p, color = color - 1)) }
                            }
                            vm.clearIncoming()
                        }
                    }.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Sb.line))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                Chip(kind.label + if (src.images.size > 1) " · ${src.images.size} images" else "", true)
                Spacer(Modifier.height(10.dp))
                Field(title, { title = it }, "Title", 26.sp, headFont(), FontWeight.Medium)
                if (img != null) {
                    Spacer(Modifier.height(12.dp))
                    ItemImage(Item(title = "", body = "", kind = kind, collection = shelf, imageUri = img), Modifier.fillMaxWidth().height(180.dp), 20.dp)
                }
                Spacer(Modifier.height(12.dp))
                Field(body, { body = it }, "Write something…", 17.sp, headFont())
            }
            Column(Modifier.fillMaxWidth().background(Sb.card).padding(16.dp)) {
                Text("Box", fontSize = 13.sp, color = Sb.mute, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                    Chip("Inbox", shelf == "Inbox") { shelf = "Inbox" }
                    if (fromPaperback && vm.shelves.value.none { it.name == PAPERBACK_BOX }) Chip(PAPERBACK_BOX, shelf == PAPERBACK_BOX) { shelf = PAPERBACK_BOX }
                    vm.shelves.value.forEach { Chip(it.name, shelf == it.name) { shelf = it.name } }
                }
            }
        }
    }
}
