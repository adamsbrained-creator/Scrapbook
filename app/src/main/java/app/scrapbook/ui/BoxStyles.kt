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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The box looks offered under Settings > Box style. The position in this list is what gets saved, so only ever add to the end.
 * 0 and 1 are drawn by TileBox in Home.kt; 2 to 6 are drawn here.
 */
val BOX_STYLES = listOf("Tabbed", "Paper stack", "Tinted tiles", "Folder tabs", "Chips", "List rows", "Notebook covers")

/** One box as the home and box screens show it: its name, how many scraps are in it and its color. */
data class BoxTile(val name: String, val count: Int, val tab: Color)

private fun scrapsOf(n: Int) = if (n == 1) "1 scrap" else "$n scraps"

/** Text that sits on a solid box color or on a white plate. Fixed, so it stays readable in the dark theme too. */
private val OnColor = Color(0xFF161616)
private val OnColorSoft = Color(0xFF5F5F5F)

private fun Modifier.boxLabel(name: String, count: Int): Modifier = semantics(mergeDescendants = true) { contentDescription = "$name, ${scrapsOf(count)}" }

/** A dashed outline for the "New box" buttons. */
private fun Modifier.dashedOutline(color: Color, shape: RoundedCornerShape): Modifier = drawBehind {
    val o = shape.createOutline(size, layoutDirection, this) as? Outline.Rounded ?: return@drawBehind
    drawPath(Path().apply { addRoundRect(o.roundRect) }, color,
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
}

/** Lays the boxes out in the current Box style: 4 across for Tabbed and Paper stack, 3 for Tinted tiles and Folder tabs, a wrapping row for Chips, full-width rows, or 2 across for Notebook covers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxGrid(tiles: List<BoxTile>, onHold: (String) -> Unit, onNew: (() -> Unit)? = null, onOpen: (String) -> Unit) {
    when (val style = LocalBoxStyle.current.coerceIn(0, BOX_STYLES.lastIndex)) {
        4 -> FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp), Arrangement.spacedBy(10.dp)) {
            tiles.forEach { BoxChip(it, { onHold(it.name) }) { onOpen(it.name) } }
            onNew?.let { NewBoxChip(it) }
        }
        5 -> Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            tiles.forEach { BoxListRow(it, { onHold(it.name) }) { onOpen(it.name) } }
        }
        else -> {
            val cols = when (style) { 2, 3 -> 3; 6 -> 2; else -> 4 }
            val gap = when (style) { 2 -> 12.dp; 6 -> 14.dp; else -> 10.dp }
            // A null cell is the dashed "New box" button, which only the Notebook covers style draws inside its grid.
            val cells = buildList<BoxTile?> { addAll(tiles); if (style == 6 && onNew != null) add(null) }
            Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
                cells.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
                        row.forEach { c ->
                            val m = Modifier.weight(1f)
                            if (c == null) onNew?.let { NewBoxCover(m, it) }
                            else when (style) {
                                2 -> BoxTinted(c, m, { onHold(c.name) }) { onOpen(c.name) }
                                3 -> BoxFolder(c, m, { onHold(c.name) }) { onOpen(c.name) }
                                6 -> BoxCover(c, m, { onHold(c.name) }) { onOpen(c.name) }
                                else -> TileBox(c.name, c.count, c.tab, m, { onHold(c.name) }) { onOpen(c.name) }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Tinted tiles: a soft color fill with a big count. */
@Composable
private fun BoxTinted(b: BoxTile, modifier: Modifier, onHold: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(modifier.aspectRatio(104f / 112f).clip(shape).background(b.tab.copy(alpha = if (Sb.dark) .28f else .22f))
        .tapOrHold(b.name, "Delete box", onHold, onClick).boxLabel(b.name, b.count).padding(14.dp), Arrangement.SpaceBetween) {
        Text("${b.count}", fontSize = 32.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp)
        Text(b.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Folder tabs: a real folder shape with a tab, in the box color. */
@Composable
private fun BoxFolder(b: BoxTile, modifier: Modifier, onHold: () -> Unit, onClick: () -> Unit) {
    Box(modifier.aspectRatio(104f / 96f)) {
        Box(Modifier.align(Alignment.TopStart).width(48.dp).height(22.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(b.tab))
        Column(Modifier.fillMaxSize().padding(top = 12.dp).clip(RoundedCornerShape(topStart = 0.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
            .background(b.tab).tapOrHold(b.name, "Delete box", onHold, onClick).boxLabel(b.name, b.count).padding(horizontal = 12.dp, vertical = 10.dp), Arrangement.Bottom) {
            Text(b.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OnColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(scrapsOf(b.count), fontSize = 13.sp, color = OnColor, maxLines = 1)
        }
    }
}

/** Chips: one slim pill per box, wrapping onto new lines. */
@Composable
private fun BoxChip(b: BoxTile, onHold: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Sb.card).tapOrHold(b.name, "Delete box", onHold, onClick).boxLabel(b.name, b.count)
        .padding(start = 14.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(b.tab)); Spacer(Modifier.width(8.dp))
        Text(b.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp)); Spacer(Modifier.width(8.dp))
        Text("${b.count}", fontSize = 14.sp, color = Sb.mute)
    }
}

@Composable
private fun NewBoxChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.height(44.dp).clip(shape).dashedOutline(Sb.mute.copy(alpha = .5f), shape).tap("New box", onClick).padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic("plus", 18.dp, Sb.mute); Spacer(Modifier.width(6.dp)); Text("New box", fontSize = 15.sp, color = Sb.mute)
    }
}

/** List rows: one full-width row per box with a folder icon, like the lists elsewhere in the app. */
@Composable
private fun BoxListRow(b: BoxTile, onHold: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(Sb.card).tapOrHold(b.name, "Delete box", onHold, onClick).boxLabel(b.name, b.count)
        .padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(b.tab.copy(alpha = if (Sb.dark) .28f else .25f)), Alignment.Center) {
            Ic("folder", 22.dp, if (Sb.dark) b.tab else lerp(b.tab, Color.Black, .35f))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(b.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(scrapsOf(b.count), fontSize = 13.sp, color = Sb.mute, maxLines = 1)
        }
        Ic("chevron", 18.dp, Sb.mute)
    }
}

/** Notebook covers: a bound spine with punched holes and a white label plate. */
@Composable
private fun BoxCover(b: BoxTile, modifier: Modifier, onHold: () -> Unit, onClick: () -> Unit) {
    Box(modifier.height(96.dp).clip(COVER_SHAPE).background(b.tab).tapOrHold(b.name, "Delete box", onHold, onClick).boxLabel(b.name, b.count)) {
        Box(Modifier.align(Alignment.TopStart).fillMaxHeight().width(16.dp).background(Color.Black.copy(alpha = .14f)))
        listOf(16, 45, 74).forEach { y ->
            Box(Modifier.align(Alignment.TopStart).offset(x = 5.dp, y = y.dp).size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = .75f)))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 28.dp, end = 12.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(b.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = OnColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(scrapsOf(b.count), fontSize = 12.sp, color = OnColorSoft, maxLines = 1)
        }
    }
}

private val COVER_SHAPE = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp)

@Composable
private fun NewBoxCover(modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(96.dp).clip(COVER_SHAPE).dashedOutline(Sb.mute.copy(alpha = .5f), COVER_SHAPE).tap("New box", onClick), Arrangement.Center, Alignment.CenterVertically) {
        Ic("plus", 18.dp, Sb.mute); Spacer(Modifier.width(6.dp)); Text("New box", fontSize = 15.sp, color = Sb.mute)
    }
}
