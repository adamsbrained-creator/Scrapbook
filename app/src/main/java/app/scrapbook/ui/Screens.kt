package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*

@Composable
private fun GBar(w: Float, h: Int, c: Color) { Box(Modifier.fillMaxWidth(w).height(h.dp).clip(RoundedCornerShape(2.dp)).background(c)) }

/** A tiny wireframe of each page layout. */
@Composable
private fun LayoutGlyph(l: Layout) {
    val t = LocalSb.current; val bar = Sb.mute.copy(alpha = .55f); val ln = Sb.mute.copy(alpha = .3f); val img = t.acc.copy(alpha = .4f)
    Column(Modifier.size(58.dp, 44.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        when (l) {
            Layout.Classic -> { GBar(.6f, 5, bar); GBar(1f, 3, ln); GBar(1f, 3, ln); GBar(.7f, 3, ln) }
            Layout.Journal -> {
                GBar(.35f, 3, ln); GBar(.7f, 5, bar); GBar(1f, 3, ln); GBar(.8f, 3, ln)
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) { repeat(3) { Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(img)) } }
            }
            Layout.Gallery -> { Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(4.dp)).background(img)); GBar(.5f, 3, bar); GBar(1f, 3, ln) }
            Layout.TwoColumn -> {
                GBar(.5f, 5, bar)
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                    repeat(2) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { GBar(1f, 3, ln); GBar(1f, 3, ln); GBar(.7f, 3, ln); GBar(1f, 3, ln) } }
                }
            }
            Layout.Quote -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) { GBar(.8f, 5, bar); GBar(.6f, 5, bar); Spacer(Modifier.height(4.dp)); GBar(.3f, 3, ln) }
            Layout.Reference -> {
                GBar(.5f, 5, bar)
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(3.dp)) { GBar(1f, 3, ln); GBar(1f, 3, ln); GBar(.7f, 3, ln) }
                    Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(3.dp)).background(ln))
                }
            }
            Layout.Collection -> {
                Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).background(img))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) { repeat(3) { Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(2.dp)).background(ln)) } }
            }
            Layout.Blank -> Box(Modifier.fillMaxSize()) {
                Box(Modifier.align(Alignment.TopStart).size(26.dp, 16.dp).rotate(-4f).clip(RoundedCornerShape(2.dp)).background(img))
                Box(Modifier.align(Alignment.CenterEnd).size(24.dp, 14.dp).rotate(3f).clip(RoundedCornerShape(2.dp)).background(bar))
                Box(Modifier.align(Alignment.BottomStart).size(20.dp, 10.dp).clip(RoundedCornerShape(2.dp)).background(ln))
            }
        }
    }
}

private fun shortLabel(l: Layout) = when (l) { Layout.TwoColumn -> "Columns"; Layout.Blank -> "Canvas"; else -> l.label }

/** Pick how a note's page is laid out. The preview updates as you tap; nothing is lost when you switch. */
@Composable
fun LayoutScreen(vm: ArchiveViewModel, nav: NavController, id: Long) {
    val t = LocalSb.current; val list by vm.items.collectAsState()
    val item = list.firstOrNull { it.id == id } ?: list.firstOrNull()
    Scaffold(containerColor = t.paper) { pad ->
        if (item != null) Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).tap("Go back") { nav.popBackStack() }, Alignment.Center) { Ic("back", 28.dp, Sb.ink) }
                Text("Choose a layout", Modifier.weight(1f), textAlign = TextAlign.Center, fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 20.sp)
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(28.dp)).background(pageTint(item.color) ?: Sb.card).padding(18.dp)) { ItemPage(item, list) }
            Spacer(Modifier.height(16.dp))
            Layout.entries.chunked(4).forEach { row ->
                Row(Modifier.padding(bottom = 8.dp), Arrangement.spacedBy(8.dp)) {
                    row.forEach { l ->
                        val on = l == item.layout
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Sb.card)
                            .border(if (on) 2.dp else 1.dp, if (on) Sb.ink else Sb.line, RoundedCornerShape(20.dp)).tap(l.label) { vm.setLayout(item.id, l) }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LayoutGlyph(l)
                            Text(shortLabel(l), fontSize = 12.sp, maxLines = 1, color = if (on) Sb.ink else Sb.mute, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }
            Text("Spacing", fontSize = 13.sp, color = Sb.mute, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Seg(listOf("Airy", "Cozy", "Compact"), item.density) { vm.save(item.copy(density = it)) }
            Spacer(Modifier.weight(1f))
            Text("Use ${item.layout.label}", Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(28.dp)).background(Sb.ink).tap("Done") {
                    // The editor is a plain writing screen, so after choosing a layout from there, open the page that shows it.
                    val fromEditor = nav.previousBackStackEntry?.destination?.route == "edit/{id}"
                    nav.popBackStack()
                    if (fromEditor) nav.navigate("item/${item.id}")
                }.padding(vertical = 16.dp),
                color = Sb.card, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, fontSize = 16.sp)
        }
    }
}
