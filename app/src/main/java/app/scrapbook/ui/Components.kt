package app.scrapbook.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scrapbook.Item
import app.scrapbook.Kind
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/** Flat illustrated placeholder: sky, sun, hill. Swap for real images (Coil) later. */
@Composable
fun FlatImage(i: Int, modifier: Modifier = Modifier, radius: Dp = LocalSb.current.radius) {
    val p = Sb.palettes[i % Sb.palettes.size]
    Canvas(modifier.clip(RoundedCornerShape(radius))) {
        drawRect(p[0])
        drawCircle(p[1], size.width * .13f, Offset(size.width * .78f, size.height * .24f))
        drawOval(p[2], Offset(-size.width * .12f, size.height * .7f), Size(size.width * 1.24f, size.height * .7f))
    }
}

@Composable
fun Chip(text: String, on: Boolean = false, onClick: () -> Unit = {}) {
    Text(text, fontSize = 11.sp, color = if (on) Color.White else Sb.ink,
        modifier = Modifier.clip(CircleShape).background(if (on) Sb.ink else Sb.card)
            .border(1.dp, Sb.line, CircleShape).clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp))
}

@Composable
fun RoundButton(label: String, dark: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(if (dark) Sb.ink else Sb.card)
        .border(1.dp, Sb.line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (dark) Color.White else Sb.ink, fontSize = 16.sp)
    }
}

@Composable
fun FanStack(items: List<Item>) {
    val xs = listOf(-86, -44, 0, 46, 88); val rs = listOf(-12f, -6f, 2f, 7f, 13f)
    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
        items.take(5).forEachIndexed { i, it ->
            Box(Modifier.offset(x = xs[i].dp).rotate(rs[i]).size(82.dp, 112.dp)
                .clip(RoundedCornerShape(LocalSb.current.radius)).background(Sb.notes[it.color % 5])) {
                if (it.kind == Kind.Image) ItemImage(it, Modifier.fillMaxSize())
                else Text(it.title, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
fun BottomBar(onShelves: () -> Unit, onNew: () -> Unit, onSearch: () -> Unit = {}) {
    Row(Modifier.navigationBarsPadding().padding(12.dp), Arrangement.spacedBy(7.dp), Alignment.CenterVertically) {
        Text("Search", color = Sb.mute, fontSize = 12.sp,
            modifier = Modifier.weight(1f).clip(CircleShape).background(Sb.card).border(1.dp, Sb.line, CircleShape)
                .clickable(onClick = onSearch).padding(horizontal = 14.dp, vertical = 12.dp))
        RoundButton("▦", onClick = onShelves)
        RoundButton("+", dark = true, onClick = onNew)
    }
}

@Composable
fun RecentCard(item: Item, onClick: () -> Unit) {
    val r = LocalSb.current.radius
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(r)).background(Sb.card)
        .border(1.dp, Sb.line, RoundedCornerShape(r)).clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(5.dp).height(38.dp).clip(CircleShape).background(Sb.notes[item.color % 5]))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontFamily = headFont(), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1)
            Text(item.body, fontSize = 10.5.sp, color = Sb.mute, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Chip(item.collection)
    }
}

/** A picked photo when the item has one, otherwise the flat placeholder illustration. */
@Composable
fun ItemImage(item: Item, modifier: Modifier = Modifier, radius: Dp = LocalSb.current.radius) {
    if (item.imageUri != null) AsyncImage(item.imageUri, null, modifier.clip(RoundedCornerShape(radius)), contentScale = ContentScale.Crop)
    else FlatImage(item.color, modifier, radius)
}
