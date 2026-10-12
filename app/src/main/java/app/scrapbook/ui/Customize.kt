package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scrapbook.*

@Composable
fun Seg(options: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Sb.line.copy(alpha = .6f)).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            Text(o, Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (i == sel) Sb.ink else Color.Transparent)
                .tap { onSel(i) }.semantics { selected = i == sel }.padding(vertical = 10.dp),
                fontSize = 14.sp, textAlign = TextAlign.Center, color = if (i == sel) Sb.card else Sb.ink)
        }
    }
}

/** A rounded gray box around a text field. */
@Composable
fun InputBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sb.line.copy(alpha = .55f)).padding(horizontal = 16.dp, vertical = 14.dp)) { content() }
}

@Composable private fun Lb(text: String) = Text(text, fontSize = 13.sp, color = Sb.mute, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Swatch(c: Color, on: Boolean, name: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(c).border(if (on) 2.dp else 1.dp, if (on) Sb.ink else Sb.line, CircleShape).tap(name, onClick).semantics { selected = on })
}

@Composable
fun Sheet(title: String, onDone: () -> Unit, doneLabel: String = "Done", primary: (() -> Unit)? = null, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onDone() }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).tap(onClick = onDone)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 680.dp)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Sb.card)
            .pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Sb.line))
            Text(title, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 22.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            Text(doneLabel, Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(28.dp)).background(Sb.ink.copy(alpha = if (enabled) 1f else .35f))
                .tap(doneLabel) { if (enabled) (primary ?: onDone)() }.padding(vertical = 16.dp),
                color = Sb.card, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
    }
}

/** Per-page style: color, type, density, visible metadata, layout. Saved on the item itself. */
@Composable
fun CustomizeSheet(item: Item, vm: ArchiveViewModel, onDone: () -> Unit) = Sheet("Customize page", onDone) {
    Lb("Page color")
    ColorDots(item.color) { vm.save(item.copy(color = it)) }
    Lb("Accent")
    AccentDots(item.accent) { vm.save(item.copy(accent = it)) }
    Lb("Cover image")
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.importImage(u) { p -> vm.items.value.firstOrNull { it.id == item.id }?.let { cur -> vm.save(cur.copy(cover = p)) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Chip(if (item.cover == null) "Choose cover" else "Change cover") { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        if (item.cover != null) Chip("Remove") { vm.save(item.copy(cover = null)) }
    }
    Lb("Type"); Seg(listOf("Theme", "Serif", "Sans", "Mono"), item.font) { vm.save(item.copy(font = it)) }
    Lb("Density"); Seg(listOf("Airy", "Cozy", "Compact"), item.density) { vm.save(item.copy(density = it)) }
    Lb("Show")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Chip("Metadata", item.showMeta) { vm.save(item.copy(showMeta = !item.showMeta)) }
        Chip("Pinned", item.pinned) { vm.togglePin(item) }
    }
    Lb("Layout")
    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(5.dp)) {
        Layout.entries.forEach { l -> Chip(l.label, item.layout == l) { vm.setLayout(item.id, l) } }
    }
}

/** App-wide style: accent, heading font, corner radius. Persisted with DataStore. */
@Composable
fun AppStyleSheet(vm: ArchiveViewModel, onDone: () -> Unit) {
    val t = LocalSb.current; val mode by vm.darkMode.collectAsState(); val header by vm.headerStyle.collectAsState(); val navMode by vm.navStyle.collectAsState(); val editorMode by vm.editorStyle.collectAsState(); val ctx = LocalContext.current; val scope = rememberCoroutineScope(); var msg by remember { mutableStateOf("") }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.restoreBackup(u) { n -> msg = if (n < 0) "Couldn't read that file." else "Restored $n new items." }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        if (us.isNotEmpty()) vm.importTexts(us) { n -> msg = "Imported $n scraps." }
    }
    Sheet("App style", onDone) {
        Lb("Accent")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Themes.forEachIndexed { i, th -> Swatch(th.acc, th.name == t.name, th.name) { vm.setAccent(i) } } }
        Lb("Headings"); Seg(listOf("Serif", "Sans"), if (t.serif) 0 else 1) { vm.setSerif(it == 0) }
        Lb("Corners"); Seg(listOf("Sharp", "Soft", "Round"), when (t.radius.value.toInt()) { 4 -> 0; 24 -> 2; else -> 1 }) { vm.setRadius(listOf(4, 14, 24)[it]) }
        Lb("Appearance"); Seg(listOf("System", "Light", "Dark"), mode) { vm.setDark(it) }
        Lb("Main page header"); Seg(listOf("Left", "Center", "Round"), header) { vm.setHeader(it) }
        Lb("Add button"); Seg(listOf("Pill", "Bottom bar"), navMode) { vm.setNav(it) }
        Lb("Editor"); Seg(listOf("Selection bubble", "Floating pill"), editorMode) { vm.setEditorStyle(it) }
        Lb("Backup")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Chip("Back up archive") {
                scope.launch {
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", vm.buildBackup())
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                        .apply { type = "application/zip"; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Back up archive"))
                }
            }
            Chip("Restore") { restore.launch(arrayOf("application/zip", "application/octet-stream")) }
            Chip("Import scraps") { importer.launch(arrayOf("text/*", "application/octet-stream")) }
        }
        if (msg.isNotEmpty()) Text(msg, fontSize = 11.sp, color = Sb.mute)
    }
}

/** Shown once on first launch. */
@Composable
fun Welcome(onDone: () -> Unit, onExamples: () -> Unit) = Sheet("Welcome to Scrapbook", onDone) {
    listOf("Tap New scrap to start writing. Select text to format it, and use the bar at the bottom for lists, quotes and checklists. It saves as you type.",
        "Every page can change layout and style without losing a word.",
        "Tap the menu (three lines) to restyle the app, or back it up.").forEach {
        Text(it, fontSize = 15.sp, lineHeight = 22.sp)
    }
    Chip("Add a few example scraps") { onExamples(); onDone() }
}
