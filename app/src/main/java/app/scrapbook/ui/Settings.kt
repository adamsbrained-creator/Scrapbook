package app.scrapbook.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import app.scrapbook.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The Settings page follows the shared Settings mockup used across the apps: a round-button top bar, a summary card with a
// progress ring, small uppercase section labels, and grouped cards of rows (icon tile, label, value, chevron or switch).
// Choices open a picker sheet. The dark theme uses the same structure with dark tokens.

/** Switch track when off. */
private val trackOff get() = if (Sb.dark) Color(0xFF3A3A3A) else Color(0xFFCFCFCF)

/** One setting. label and keywords are what the search box matches. */
private class Setting(val label: String, val keywords: String = "", val content: @Composable () -> Unit)
private class SettingGroup(val title: String, val items: List<Setting>, val footer: String? = null)

/** A round 40dp button inside a 44dp touch target, used in the headers of Settings and About. */
@Composable
internal fun CircleBtn(desc: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).tap(desc, onClick), Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Sb.card), Alignment.Center) { content() }
    }
}

/** The small "| Scrapbook" mark in the middle of the header. */
@Composable
internal fun BrandBar() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(21.dp).clip(RoundedCornerShape(1.dp)).background(LocalSb.current.acc))
        Spacer(Modifier.width(3.dp))
        Text("Scrapbook", fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = (-0.02).em, maxLines = 1)
    }
}

/** The big page title under the header. */
@Composable
internal fun PageTitle(text: String) {
    Text(text, Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp), fontFamily = headFont(), fontWeight = FontWeight.Medium,
        fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-0.02).em)
}

/** A row that opens something. value is shown in grey before the icon, which is a chevron, or "external" for a link that leaves the app. */
@Composable
internal fun NavRow(label: String, value: String = "", icon: String = "chevron", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).tap(label, onClick).padding(start = 18.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        if (value.isNotEmpty()) Text(value, fontSize = 15.sp, color = Sb.mute)
        Spacer(Modifier.width(8.dp))
        Ic(icon, 18.dp, Sb.mute)
    }
}

/** A rounded grey card holding a group of rows. Put a RowDivider between rows. */
@Composable
internal fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Sb.card), content = content)
}

@Composable
internal fun RowDivider() {
    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Sb.line))
}

/** A grouped card with 32dp corners. Put a SettingDivider between its rows. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Sb.card), content = content)
}

/** The hairline between rows, starting after the icon tile. */
@Composable
private fun SettingDivider() {
    Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(1.dp).background(Sb.line))
}

/** Small uppercase label above a card. */
@Composable
private fun SectionLabel(title: String) {
    Text(title.uppercase(), Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.4.sp, color = Sb.mute)
}

/** The 40dp rounded tile behind a row's icon. It takes the page color, so it sits slightly below the card. */
@Composable
private fun IconTile(icon: String) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Sb.paper), Alignment.Center) { Ic(icon, 20.dp, Sb.ink) }
}

/** A row that shows the current choice in grey (with an optional color dot) and opens a picker or page when tapped. */
@Composable
private fun IconRow(icon: String, label: String, value: String = "", dot: Color? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).tap(label, onClick).padding(horizontal = 16.dp), Arrangement.spacedBy(14.dp), Alignment.CenterVertically) {
        IconTile(icon)
        Text(label, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (dot != null || value.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
            if (value.isNotEmpty()) Text(value, fontSize = 15.sp, color = Sb.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Ic("chevron", 20.dp, Sb.mute)
    }
}

/** A row with an on/off switch instead of a chevron. */
@Composable
private fun SwitchRow(icon: String, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val acc = LocalSb.current.acc
    Row(Modifier.fillMaxWidth().height(64.dp).toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(horizontal = 16.dp),
        Arrangement.spacedBy(14.dp), Alignment.CenterVertically) {
        IconTile(icon)
        Text(label, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.size(50.dp, 30.dp).clip(CircleShape).background(if (on) acc else trackOff), if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(3.dp).size(24.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
        }
    }
}

/** The card at the top: a ring showing how much of the archive is filed in boxes, a line of text and a button to the boxes. */
@Composable
private fun SummaryCard(filed: Int, total: Int, onView: () -> Unit) {
    val acc = LocalSb.current.acc
    val frac = if (total == 0) 0f else filed.toFloat() / total
    val track = Sb.ink.copy(alpha = .12f)
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(112.dp).clip(RoundedCornerShape(32.dp)).background(Sb.card).padding(horizontal = 18.dp),
        Arrangement.spacedBy(18.dp), Alignment.CenterVertically) {
        Box(Modifier.size(76.dp), Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 8.dp.toPx(); val r = 31.dp.toPx(); val tl = Offset(size.width / 2 - r, size.height / 2 - r); val sz = Size(2 * r, 2 * r)
                drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(sw))
                if (frac > 0f) drawArc(acc, -90f, 360f * frac, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text("${(frac * 100).roundToInt()}%", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Organized", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (total == 0) "No scraps yet" else "$filed of $total in boxes", fontSize = 14.sp, color = Sb.mute)
        }
        Box(Modifier.height(48.dp).clip(CircleShape).background(Sb.ink).tap("Open boxes", onView).padding(horizontal = 22.dp), Alignment.Center) {
            Text("Boxes", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Sb.paper)
        }
    }
}

/** What a picker sheet needs: the title, the choices, the current one, and what to do when one is chosen. dots shows a color next to each choice. */
private class Pick(val title: String, val options: List<String>, val sel: Int, val dots: List<Color>? = null, val note: String? = null, val onPick: (Int) -> Unit)

/** Pick one of a few options. Choosing one applies it and closes the sheet. */
@Composable
private fun PickerSheet(p: Pick, onDone: () -> Unit) {
    val acc = LocalSb.current.acc
    Sheet(p.title, onDone, doneLabel = "Cancel") {
        p.options.forEachIndexed { i, o ->
            Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Sb.line.copy(alpha = .5f))
                .tap(o) { p.onPick(i); onDone() }.semantics { selected = i == p.sel }.padding(horizontal = 20.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    p.dots?.getOrNull(i)?.let { Box(Modifier.size(16.dp).clip(CircleShape).background(it)) }
                    Text(o, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                if (i == p.sel) Ic("check", 22.dp, acc)
            }
        }
        if (p.note != null) Text(p.note, Modifier.padding(horizontal = 6.dp), fontSize = 12.sp, color = Sb.mute)
    }
}

@Composable
fun SettingsScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val mode by vm.darkMode.collectAsState(); val grey by vm.greyBg.collectAsState(); val headerSize by vm.headerSize.collectAsState()
    val defaultGrid by vm.defaultGrid.collectAsState(); val sortTitle by vm.sortTitle.collectAsState(); val sortAsc by vm.sortAsc.collectAsState()
    val boxStyle by vm.boxStyle.collectAsState(); val showPreview by vm.showPreview.collectAsState(); val showBoxes by vm.showBoxes.collectAsState()
    val reopenLast by vm.reopenLastBox.collectAsState()
    val boxSearch by vm.showBoxSearch.collectAsState()
    val headerLayout by vm.headerStyle.collectAsState(); val navMode by vm.navStyle.collectAsState(); val editorMode by vm.editorStyle.collectAsState()
    val shelves by vm.shelves.collectAsState(); val scraps by vm.items.collectAsState()
    var msg by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }; var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    BackHandler(searching) { searching = false; q = "" }

    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.restoreBackup(u) { n -> msg = if (n < 0) "Couldn't read that file." else "Restored $n new items." }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        if (us.isNotEmpty()) vm.importTexts(us) { n -> msg = "Imported $n scraps." }
    }

    // Sorting. Choosing a sort field also resets the order to that field's natural one (newest first, A to Z).
    val orderOptions = if (sortTitle) listOf("A to Z", "Z to A") else listOf("Newest first", "Oldest first")
    val orderIndex = if (sortTitle) (if (sortAsc) 0 else 1) else (if (sortAsc) 1 else 0)
    val boxStyles = BOX_STYLES
    val cornerIndex = when (t.radius.value.toInt()) { 4 -> 0; 24 -> 2; else -> 1 }
    val themeOptions = listOf("System", "Light", "Dark"); val sizeOptions = listOf("Compact", "Regular", "Large")
    val headerOptions = listOf("Left", "Center", "Round"); val addOptions = listOf("Pill", "Bottom bar"); val editorOptions = listOf("Selection bubble", "Floating pill")
    val bgOptions = listOf("White", "Soft grey"); val viewOptions = listOf("List", "Grid"); val headingOptions = listOf("Serif", "Sans"); val cornerOptions = listOf("Sharp", "Soft", "Round")

    // Every choice opens one of these sheets.
    val picks = mapOf(
        "theme" to Pick("Theme", themeOptions, mode) { vm.setDark(it) },
        "accent" to Pick("Accent color", Themes.map { it.name }, Themes.indexOfFirst { it.name == t.name }, dots = Themes.map { it.acc }) { vm.setAccent(it) },
        "bg" to Pick("Background", bgOptions, if (grey) 1 else 0, note = "The dark theme always uses a black background.") { vm.setGreyBg(it == 1) },
        "headerSize" to Pick("Header size", sizeOptions, headerSize) { vm.setHeaderSize(it) },
        "headings" to Pick("Headings", headingOptions, if (t.serif) 0 else 1) { vm.setSerif(it == 0) },
        "corners" to Pick("Corners", cornerOptions, cornerIndex) { vm.setRadius(listOf(4, 14, 24)[it]) },
        "view" to Pick("Default view", viewOptions, if (defaultGrid) 1 else 0) { vm.setDefaultGrid(it == 1) },
        "sortBy" to Pick("Sort by", listOf("Date edited", "Title"), if (sortTitle) 1 else 0) { i -> if ((i == 1) != sortTitle) vm.setSort(i == 1, i == 1) },
        "sortOrder" to Pick("Sort order", orderOptions, orderIndex) { i -> vm.setSortAsc(if (sortTitle) i == 0 else i == 1) },
        "boxStyle" to Pick("Box style", boxStyles, boxStyle.coerceIn(0, boxStyles.lastIndex)) { vm.setBoxStyle(it) },
        "header" to Pick("Main page header", headerOptions, headerLayout) { vm.setHeader(it) },
        "add" to Pick("Add button", addOptions, navMode) { vm.setNav(it) },
        "editor" to Pick("Editor toolbar", editorOptions, editorMode) { vm.setEditorStyle(it) },
    )

    val groups = listOf(
        SettingGroup("Look", listOf(
            Setting("Theme", "appearance mode light dark system") { IconRow("palette", "Theme", themeOptions[mode.coerceIn(0, 2)]) { picker = "theme" } },
            Setting("Accent color", "colour theme coral teal amber violet green pink") { IconRow("accent", "Accent color", t.name, dot = t.acc) { picker = "accent" } },
            Setting("Background", "page white grey gray") { IconRow("page", "Background", bgOptions[if (grey) 1 else 0]) { picker = "bg" } },
            Setting("Header size", "title wordmark compact regular large") { IconRow("sliders", "Header size", sizeOptions[headerSize.coerceIn(0, 2)]) { picker = "headerSize" } },
            Setting("Headings", "font serif sans type") { IconRow("headsize", "Headings", headingOptions[if (t.serif) 0 else 1]) { picker = "headings" } },
            Setting("Corners", "radius sharp soft round") { IconRow("corner", "Corners", cornerOptions[cornerIndex]) { picker = "corners" } },
        )),
        SettingGroup("Home and lists", listOf(
            Setting("Default view", "list grid layout") { IconRow("grid", "Default view", viewOptions[if (defaultGrid) 1 else 0]) { picker = "view" } },
            Setting("Sort by", "order title date edited") { IconRow("sort", "Sort by", if (sortTitle) "Title" else "Date edited") { picker = "sortBy" } },
            Setting("Sort order", "newest oldest first a to z") { IconRow("down", "Sort order", orderOptions[orderIndex]) { picker = "sortOrder" } },
            Setting("Box style", "boxes tiles paper stack tabbed tinted folder chips list rows notebook covers") { IconRow("layout", "Box style", boxStyles[boxStyle.coerceIn(0, boxStyles.lastIndex)]) { picker = "boxStyle" } },
            Setting("Show note preview", "text snippet lines") { SwitchRow("list", "Show note preview", showPreview) { vm.setShowPreview(it) } },
            Setting("Main page header", "layout left center round") { IconRow("menu", "Main page header", headerOptions[headerLayout.coerceIn(0, 2)]) { picker = "header" } },
            Setting("Add button", "new scrap pill bottom bar menu button hold plus") { IconRow("plus", "Add button", addOptions[navMode.coerceIn(0, 1)]) { picker = "add" } },
        )),
        SettingGroup("Boxes", listOf(
            Setting("Manage boxes", "collections folders create delete") {
                IconRow("folder", "Manage boxes", when (shelves.size) { 0 -> "No boxes"; 1 -> "1 box"; else -> "${shelves.size} boxes" }) { nav.navigate("collections") }
            },
            Setting("Show boxes on home", "tiles collections hide") { SwitchRow("eye", "Show boxes on home", showBoxes) { vm.setShowBoxes(it) } },
            Setting("Show search in boxes", "search bar field find hide clean") { SwitchRow("search", "Show search in boxes", boxSearch) { vm.setShowBoxSearch(it) } },
            Setting("Open last box on launch", "start startup reopen remember resume folder") { SwitchRow("clock", "Open last box on launch", reopenLast) { vm.setReopenLastBox(it) } },
        )),
        SettingGroup("Editor", listOf(
            Setting("Editor toolbar", "editor selection bubble floating pill") { IconRow("edit", "Editor toolbar", editorOptions[editorMode.coerceIn(0, 1)]) { picker = "editor" } },
        )),
        SettingGroup("Data", listOf(
            Setting("Back up archive", "export save zip") {
                IconRow("backup", "Back up archive") {
                    scope.launch {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", vm.buildBackup())
                        ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                            .apply { type = "application/zip"; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Back up archive"))
                    }
                }
            },
            Setting("Restore from backup", "import zip") { IconRow("restore", "Restore from backup") { restore.launch(arrayOf("application/zip", "application/octet-stream")) } },
            Setting("Import scraps", "text markdown files") { IconRow("newfile", "Import scraps") { importer.launch(arrayOf("text/*", "application/octet-stream")) } },
        ), footer = msg.ifEmpty { "A backup is one zip with every scrap and photo." }),
    )

    val query = q.trim()
    val visible = groups.map { g ->
        g to g.items.filter { s -> query.isEmpty() || g.title.contains(query, true) || s.label.contains(query, true) || s.keywords.contains(query, true) }
    }.filter { it.second.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = t.paper) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleBtn("Go back", { nav.popBackStack() }) { Ic("back", 22.dp, Sb.ink) }
                    Box(Modifier.weight(1f), Alignment.Center) {
                        Text("scrapbook", fontFamily = headFont(), fontWeight = FontWeight.Medium, fontSize = 20.sp, letterSpacing = (-0.2).sp, maxLines = 1)
                    }
                    if (searching) CircleBtn("Close search", { searching = false; q = "" }) { Ic("close", 22.dp, Sb.ink) }
                    else CircleBtn("Search settings", { searching = true }) { Ic("search", 22.dp, Sb.ink) }
                }
                if (searching) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp).clip(RoundedCornerShape(28.dp)).background(Sb.card).padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Ic("search", 22.dp, Sb.mute); Spacer(Modifier.width(12.dp))
                        BasicTextField(q, { q = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Sb.ink), cursorBrush = SolidColor(t.acc),
                            decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search settings", color = Sb.mute, fontSize = 16.sp); inner() } })
                    }
                } else Text("Settings", Modifier.padding(start = 28.dp, top = 12.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (!searching) {
                        Spacer(Modifier.height(14.dp))
                        SummaryCard(scraps.count { it.collection.isNotBlank() && it.collection != "Inbox" }, scraps.size) { nav.navigate("collections") }
                    }
                    visible.forEach { (g, items) ->
                        SectionLabel(g.title)
                        SettingsCard {
                            items.forEachIndexed { ii, st ->
                                if (ii > 0) SettingDivider()
                                st.content()
                            }
                        }
                        if (g.footer != null) Text(g.footer, Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp), fontSize = 12.sp, color = Sb.mute)
                    }
                    if (visible.isEmpty()) Text("No settings match “$query”.", Modifier.padding(24.dp), fontSize = 15.sp, color = Sb.mute)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        picker?.let { key -> picks[key]?.let { PickerSheet(it) { picker = null } } }
    }
}
