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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
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
fun Sheet(title: String, onDone: () -> Unit, doneLabel: String = "Done", primary: (() -> Unit)? = null, enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    BackHandler { onDone() }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).tap(onClick = onDone)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 680.dp)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Sb.raised)
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

/** Shown once on first launch. */
@Composable
fun Welcome(onDone: () -> Unit, onExamples: () -> Unit) = Sheet("Welcome to Scrapbook", onDone) {
    listOf("Tap New scrap to start writing. Select text to format it, and use the bar at the bottom for lists, quotes and checklists. It saves as you type.",
        "Every page can change layout and style without losing a word.",
        "Open the menu, then Settings, to restyle the app or back it up.").forEach {
        Text(it, fontSize = 15.sp, lineHeight = 22.sp)
    }
    Chip("Add a few example scraps") { onExamples(); onDone() }
}
