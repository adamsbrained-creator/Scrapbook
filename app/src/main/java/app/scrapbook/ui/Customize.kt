package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scrapbook.*

@Composable
fun Seg(options: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFEFEDE6)).padding(2.dp)) {
        options.forEachIndexed { i, o ->
            Text(o, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (i == sel) Color.White else Color.Transparent)
                .clickable { onSel(i) }.padding(vertical = 6.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable private fun Lb(text: String) = Text(text, fontSize = 10.5.sp, color = Sb.mute, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun Swatch(c: Color, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(c).border(if (on) 2.dp else 1.dp, if (on) Sb.ink else Sb.line, CircleShape).clickable(onClick = onClick))
}

@Composable
private fun Sheet(title: String, onDone: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x55000000)).clickable(onClick = onDone)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Sb.card).pointerInput(Unit) { detectTapGestures { } }.navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            content()
            Text("Done", Modifier.fillMaxWidth().padding(top = 6.dp).clip(CircleShape).background(LocalSb.current.acc).clickable(onClick = onDone).padding(12.dp),
                color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

/** Per-page style: color, type, density, visible metadata, layout. Saved on the item itself. */
@Composable
fun CustomizeSheet(item: Item, vm: ArchiveViewModel, onDone: () -> Unit) = Sheet("Customize page", onDone) {
    Lb("Page color")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Sb.notes.forEachIndexed { i, c -> Swatch(c, item.color % 5 == i) { vm.save(item.copy(color = i)) } } }
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
    val t = LocalSb.current
    Sheet("App style", onDone) {
        Lb("Accent")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Themes.forEachIndexed { i, th -> Swatch(th.acc, th.name == t.name) { vm.setAccent(i) } } }
        Lb("Headings"); Seg(listOf("Serif", "Sans"), if (t.serif) 0 else 1) { vm.setSerif(it == 0) }
        Lb("Corners"); Seg(listOf("Sharp", "Soft", "Round"), when (t.radius.value.toInt()) { 4 -> 0; 24 -> 2; else -> 1 }) { vm.setRadius(listOf(4, 14, 24)[it]) }
    }
}
