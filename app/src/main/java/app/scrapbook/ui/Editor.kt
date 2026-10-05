package app.scrapbook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*

@Composable
private fun Field(v: String, onChange: (String) -> Unit, hint: String, size: TextUnit, font: FontFamily, weight: FontWeight = FontWeight.Normal) {
    BasicTextField(v, onChange, Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontSize = size, fontFamily = font, fontWeight = weight, color = Sb.ink, lineHeight = size * 1.4f),
        cursorBrush = SolidColor(LocalSb.current.acc),
        decorationBox = { inner -> Box { if (v.isEmpty()) Text(hint, color = Sb.mute, fontSize = size, fontFamily = font, fontWeight = weight); inner() } })
}

/** id == 0 creates a new note. Everything customizable per note: kind, collection, color, layout, pin. */
@Composable
fun EditorScreen(vm: ArchiveViewModel, nav: NavController, id: Long) {
    val t = LocalSb.current; val list by vm.items.collectAsState()
    val existing = list.firstOrNull { it.id == id }
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var body by remember(existing?.id) { mutableStateOf(existing?.body ?: "") }
    var color by remember(existing?.id) { mutableIntStateOf(existing?.color ?: 0) }
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: Kind.Note) }
    var shelf by remember(existing?.id) { mutableStateOf(existing?.collection ?: "Inbox") }
    var tags by remember(existing?.id) { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var img by remember(existing?.id) { mutableStateOf(existing?.imageUri) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.importImage(uri) { img = it } }

    Scaffold(containerColor = t.paper) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(Sb.notes[color % 5].copy(alpha = .4f))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Cancel", fontSize = 13.sp, modifier = Modifier.clickable { nav.popBackStack() })
                    if (existing != null) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (existing.pinned) "Unpin" else "Pin", fontSize = 13.sp, modifier = Modifier.clickable { vm.togglePin(existing) })
                        Text("Layout", fontSize = 13.sp, modifier = Modifier.clickable { nav.navigate("layouts/${existing.id}") })
                        Text("Delete", fontSize = 13.sp, modifier = Modifier.clickable { vm.delete(existing); nav.popBackStack("home", false) })
                    }
                    Text("Save", color = t.acc, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable {
                        val base = existing ?: Item(title = "", body = "", kind = kind, collection = shelf)
                        vm.save(base.copy(title = title.ifBlank { "Untitled" }, body = body, kind = kind, collection = shelf, color = color, savedAt = System.currentTimeMillis(),
                            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }, imageUri = img))
                        nav.popBackStack()
                    })
                }
                Text("$shelf · ${kind.name}", fontSize = 10.5.sp, color = Sb.mute)
                Spacer(Modifier.height(6.dp))
                Field(title, { title = it }, "Untitled", 26.sp, headFont(), FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Field(tags, { tags = it }, "Tags, separated by commas", 12.sp, FontFamily.SansSerif)
                if (img != null) { Spacer(Modifier.height(10.dp)); ItemImage(Item(title = "", body = "", kind = kind, collection = shelf, imageUri = img, color = color), Modifier.fillMaxWidth().height(140.dp)) }
                Spacer(Modifier.height(10.dp))
                Field(body, { body = it }, "Write, paste or drop anything…", 15.sp, FontFamily.Serif)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Sb.card).imePadding().navigationBarsPadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Sb.notes.forEachIndexed { i, c ->
                        Box(Modifier.size(24.dp).clip(CircleShape).background(c)
                            .border(if (i == color % 5) 2.dp else 1.dp, if (i == color % 5) Sb.ink else Sb.line, CircleShape).clickable { color = i })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Kind.entries.forEach { Chip(it.name, kind == it) { kind = it } }
                    vm.shelves.forEach { Chip(it.name, shelf == it.name) { shelf = it.name } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip("＋ Image") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Chip("☑ Checklist") { body = if (body.isEmpty() || body.endsWith("\n")) body + "- [ ] " else body + "\n- [ ] " }
                }
            }
        }
    }
}
