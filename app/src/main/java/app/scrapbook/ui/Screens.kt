package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.ArchiveViewModel
import app.scrapbook.Layout

@Composable
fun HomeScreen(vm: ArchiveViewModel, nav: NavController) {
    val list by vm.items.collectAsState(); val t = LocalSb.current
    var style by remember { mutableStateOf(false) }
    Scaffold(containerColor = t.paper, bottomBar = { BottomBar({ nav.navigate("collections") }, { nav.navigate("edit/0") }, { nav.navigate("search") }) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 15.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(t.tint).clickable { style = true }, Alignment.Center) { Text("J", fontSize = 13.sp) }
                Text("Scrapbook", fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 21.sp)
                Text("712", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).background(t.acc).padding(horizontal = 10.dp, vertical = 3.dp))
            }
            FanStack(list)
            Text("Pinned", color = Sb.mute, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(list.filter { it.pinned }) { ItemImage(it, Modifier.size(80.dp, 62.dp), radius = 31.dp) }
            }
            Text("From your archive  ›", fontFamily = headFont(), fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(t.radius)).background(t.tint).clickable { nav.navigate("rediscover") }.padding(12.dp))
            Text("Recent", fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            list.take(8).forEach { RecentCard(it) { nav.navigate("item/${it.id}") } }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.shelves.take(2).forEach { s ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(26.dp)).background(if (s.color == 2) Sb.notes[0] else t.tint)) {
                            FlatImage(s.color, Modifier.offset(22.dp, 20.dp).size(38.dp, 48.dp).rotate(-9f), 9.dp)
                            FlatImage(s.color + 2, Modifier.offset(48.dp, 34.dp).size(38.dp, 48.dp).rotate(6f), 9.dp)
                        }
                        Text(s.name, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Text("${list.count { it.collection == s.name }} items", fontSize = 10.5.sp, color = Sb.mute)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (style) AppStyleSheet(vm) { style = false }
    }
}

@Composable
fun CollectionsScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val all by vm.items.collectAsState()
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 15.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Collections", fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 24.sp, modifier = Modifier.padding(vertical = 10.dp))
            vm.shelves.forEach { s ->
                Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(t.radius)).background(Sb.card)
                    .border(1.dp, Sb.line, RoundedCornerShape(t.radius)).clickable { nav.navigate("shelf/${s.name}") }) {
                    Column(Modifier.padding(11.dp)) {
                        Text(s.name, fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("${all.count { it.collection == s.name }} items", fontSize = 10.5.sp, color = Sb.mute)
                    }
                    // Peeking content panels; the row is allowed to overflow and gets clipped by the card.
                    Row(Modifier.align(Alignment.BottomStart).padding(start = 11.dp).wrapContentWidth(Alignment.Start, unbounded = true),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Quotes", "Notes", "Images").forEachIndexed { i, label ->
                            Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.size(96.dp, 58.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .background(Sb.notes[(s.color + i) % 5]).padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LayoutScreen(vm: ArchiveViewModel, nav: NavController, id: Long) {
    val t = LocalSb.current; val list by vm.items.collectAsState()
    val item = list.firstOrNull { it.id == id } ?: list.first()
    var spacing by remember { mutableFloatStateOf(0.6f) }
    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.padding(pad).padding(15.dp)) {
            Text("Choose a layout", fontFamily = headFont(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
            Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(t.radius)).background(Sb.notes[item.color % 5].copy(alpha = .4f)).padding(14.dp)) {
                ItemPage(item, list)
            }
            Spacer(Modifier.height(12.dp))
            Layout.entries.chunked(4).forEach { row ->
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { l ->
                        val on = l == item.layout
                        Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (on) t.tint else Sb.card)
                            .border(1.5.dp, if (on) t.acc else Sb.line, RoundedCornerShape(11.dp))
                            .clickable { vm.setLayout(item.id, l) }, Alignment.Center) {
                            Text(l.label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Text("Spacing", fontSize = 10.5.sp, color = Sb.mute, modifier = Modifier.padding(top = 12.dp))
            Slider(spacing, { spacing = it }, colors = SliderDefaults.colors(thumbColor = t.acc, activeTrackColor = t.acc))
            Spacer(Modifier.weight(1f))
            Button({ nav.popBackStack() }, Modifier.fillMaxWidth().navigationBarsPadding(), colors = ButtonDefaults.buttonColors(containerColor = t.acc)) {
                Text("Use ${item.layout.label}")
            }
        }
    }
}
