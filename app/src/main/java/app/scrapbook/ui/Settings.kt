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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import app.scrapbook.*
import kotlinx.coroutines.launch

// The Settings page. Layout, spacing and colors follow the "Settings 1 of 2" mockup: grouped rows on a grey card, segmented
// controls, accent dots, value rows with a chevron, and switches. The dark theme uses the same structure with dark tokens.

/** The thumb of a segmented control: white in light mode (with a soft shadow), a lifted grey in dark mode. */
private val thumbColor get() = if (Sb.dark) Color(0xFF3D3D3D) else Color.White
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

@Composable
private fun SettingSeg(options: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Sb.line).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == sel
            val pill = RoundedCornerShape(18.dp)
            Box(Modifier.weight(1f).height(36.dp).then(if (on) Modifier.shadow(1.dp, pill) else Modifier).clip(pill)
                .background(if (on) thumbColor else Color.Transparent).tap(o) { onSel(i) }.semantics { selected = on }, Alignment.Center) {
                Text(o, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) Sb.ink else Sb.mute, maxLines = 1)
            }
        }
    }
}

/** A label with a segmented control under it. note is small helper text under the control. */
@Composable
private fun SegRow(label: String, options: List<String>, sel: Int, note: String? = null, onSel: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp)) {
        Text(label, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        SettingSeg(options, sel, onSel)
        if (note != null) Text(note, fontSize = 12.sp, color = Sb.mute, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AccentRow(current: String, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)) {
        Text("Accent color", fontSize = 16.sp)
        // The dots sit in 44dp touch targets, so the row is pulled left by 7dp to line the first dot up with the text.
        Row(Modifier.padding(top = 4.dp).offset(x = (-7).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Themes.forEachIndexed { i, th ->
                val on = th.name == current
                Box(Modifier.size(44.dp).tap(th.name) { onPick(i) }.semantics { selected = on }, Alignment.Center) {
                    if (on) Box(Modifier.size(40.dp).border(2.dp, Sb.ink, CircleShape))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(th.acc))
                }
            }
        }
    }
}

/** A row that opens something. value is shown in grey before the chevron. */
@Composable
private fun NavRow(label: String, value: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).tap(label, onClick).padding(start = 18.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        if (value.isNotEmpty()) Text(value, fontSize = 15.sp, color = Sb.mute)
        Spacer(Modifier.width(8.dp))
        Ic("chevron", 18.dp, Sb.mute)
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val acc = LocalSb.current.acc
    Row(Modifier.fillMaxWidth().height(56.dp).toggleable(value = on, role = Role.Switch, onValueChange = onChange).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp)
        Box(Modifier.size(50.dp, 30.dp).clip(CircleShape).background(if (on) acc else trackOff), if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(3.dp).size(24.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
        }
    }
}

/** Pick one of a few options. Choosing one applies it and closes the sheet. */
@Composable
private fun PickerSheet(title: String, options: List<String>, sel: Int, onPick: (Int) -> Unit, onDone: () -> Unit) {
    val acc = LocalSb.current.acc
    Sheet(title, onDone, doneLabel = "Cancel") {
        options.forEachIndexed { i, o ->
            Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Sb.line.copy(alpha = .5f))
                .tap(o) { onPick(i); onDone() }.semantics { selected = i == sel }.padding(horizontal = 20.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(o, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (i == sel) Ic("check", 22.dp, acc)
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: ArchiveViewModel, nav: NavController) {
    val t = LocalSb.current; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val mode by vm.darkMode.collectAsState(); val grey by vm.greyBg.collectAsState(); val headerSize by vm.headerSize.collectAsState()
    val defaultGrid by vm.defaultGrid.collectAsState(); val sortTitle by vm.sortTitle.collectAsState(); val sortAsc by vm.sortAsc.collectAsState()
    val boxStyle by vm.boxStyle.collectAsState(); val showPreview by vm.showPreview.collectAsState(); val showBoxes by vm.showBoxes.collectAsState()
    val headerLayout by vm.headerStyle.collectAsState(); val navMode by vm.navStyle.collectAsState(); val editorMode by vm.editorStyle.collectAsState()
    val shelves by vm.shelves.collectAsState()
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

    val groups = listOf(
        SettingGroup("App style", listOf(
            Setting("Theme", "appearance mode light dark system") { SegRow("Theme", listOf("System", "Light", "Dark"), mode) { vm.setDark(it) } },
            Setting("Background", "page white grey gray") {
                SegRow("Background", listOf("White", "Soft grey"), if (grey) 1 else 0, note = if (Sb.dark) "The dark theme always uses a black background." else null) { vm.setGreyBg(it == 1) }
            },
            Setting("Accent color", "colour theme coral teal amber violet green pink") { AccentRow(t.name) { vm.setAccent(it) } },
            Setting("Header size", "title wordmark compact regular large") { SegRow("Header size", listOf("Compact", "Regular", "Large"), headerSize) { vm.setHeaderSize(it) } },
        )),
        SettingGroup("Home and lists", listOf(
            Setting("Default view", "list grid layout") { SegRow("Default view", listOf("List", "Grid"), if (defaultGrid) 1 else 0) { vm.setDefaultGrid(it == 1) } },
            Setting("Sort by", "order title date edited") { NavRow("Sort by", if (sortTitle) "Title" else "Date edited") { picker = "sortBy" } },
            Setting("Sort order", "newest oldest first a to z") { NavRow("Sort order", orderOptions[orderIndex]) { picker = "sortOrder" } },
            Setting("Box style", "boxes tiles paper stack tabbed tinted folder chips list rows notebook covers") { NavRow("Box style", boxStyles[boxStyle.coerceIn(0, boxStyles.lastIndex)]) { picker = "boxStyle" } },
            Setting("Show note preview", "text snippet lines") { ToggleRow("Show note preview", showPreview) { vm.setShowPreview(it) } },
        )),
        SettingGroup("Boxes", listOf(
            Setting("Manage boxes", "collections folders create delete") {
                NavRow("Manage boxes", when (shelves.size) { 0 -> "No boxes"; 1 -> "1 box"; else -> "${shelves.size} boxes" }) { nav.navigate("collections") }
            },
            Setting("Show boxes on home", "tiles collections hide") { ToggleRow("Show boxes on home", showBoxes) { vm.setShowBoxes(it) } },
        )),
        SettingGroup("More style", listOf(
            Setting("Headings", "font serif sans type") { SegRow("Headings", listOf("Serif", "Sans"), if (t.serif) 0 else 1) { vm.setSerif(it == 0) } },
            Setting("Corners", "radius sharp soft round") {
                SegRow("Corners", listOf("Sharp", "Soft", "Round"), when (t.radius.value.toInt()) { 4 -> 0; 24 -> 2; else -> 1 }) { vm.setRadius(listOf(4, 14, 24)[it]) }
            },
            Setting("Main page header", "layout left center round") { SegRow("Main page header", listOf("Left", "Center", "Round"), headerLayout) { vm.setHeader(it) } },
            Setting("Add button", "new scrap pill bottom bar menu button hold plus") { SegRow("Add button", listOf("Pill", "Bottom bar"), navMode) { vm.setNav(it) } },
            Setting("Editor toolbar", "editor selection bubble floating pill") { SegRow("Editor toolbar", listOf("Selection bubble", "Floating pill"), editorMode) { vm.setEditorStyle(it) } },
        )),
        SettingGroup("Backup", listOf(
            Setting("Back up archive", "export save zip") {
                NavRow("Back up archive") {
                    scope.launch {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", vm.buildBackup())
                        ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                            .apply { type = "application/zip"; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Back up archive"))
                    }
                }
            },
            Setting("Restore from backup", "import zip") { NavRow("Restore from backup") { restore.launch(arrayOf("application/zip", "application/octet-stream")) } },
            Setting("Import scraps", "text markdown files") { NavRow("Import scraps") { importer.launch(arrayOf("text/*", "application/octet-stream")) } },
        ), footer = msg.ifEmpty { "A backup is one zip with every scrap and photo." }),
    )

    val query = q.trim()
    val visible = groups.map { g ->
        g to g.items.filter { s -> query.isEmpty() || g.title.contains(query, true) || s.label.contains(query, true) || s.keywords.contains(query, true) }
    }.filter { it.second.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = t.paper) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleBtn("Go back", { nav.popBackStack() }) { Ic("back", 22.dp, Sb.ink) }
                    Box(Modifier.weight(1f), Alignment.Center) { BrandBar() }
                    if (searching) CircleBtn("Close search", { searching = false; q = "" }) { Text("✕", fontSize = 16.sp, color = Sb.ink) }
                    else CircleBtn("Search settings", { searching = true }) { Ic("search", 22.dp, Sb.ink) }
                }
                if (searching) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp).clip(RoundedCornerShape(28.dp)).background(Sb.card).padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Ic("search", 22.dp, Sb.mute); Spacer(Modifier.width(12.dp))
                        BasicTextField(q, { q = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = Sb.ink), cursorBrush = SolidColor(t.acc),
                            decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search settings", color = Sb.mute, fontSize = 16.sp); inner() } })
                    }
                } else PageTitle("Settings")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    visible.forEachIndexed { gi, (g, items) ->
                        Text(g.title, Modifier.padding(start = 24.dp, end = 24.dp, top = if (gi == 0) 22.dp else 26.dp, bottom = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Sb.mute)
                        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Sb.card)) {
                            items.forEachIndexed { ii, s ->
                                if (ii > 0) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Sb.line))
                                s.content()
                            }
                        }
                        if (g.footer != null) Text(g.footer, Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp), fontSize = 12.sp, color = Sb.mute)
                    }
                    if (visible.isEmpty()) Text("No settings match “$query”.", Modifier.padding(24.dp), fontSize = 15.sp, color = Sb.mute)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        when (picker) {
            "sortBy" -> PickerSheet("Sort by", listOf("Date edited", "Title"), if (sortTitle) 1 else 0, { i -> if ((i == 1) != sortTitle) vm.setSort(i == 1, i == 1) }) { picker = null }
            "sortOrder" -> PickerSheet("Sort order", orderOptions, orderIndex, { i -> vm.setSortAsc(if (sortTitle) i == 0 else i == 1) }) { picker = null }
            "boxStyle" -> PickerSheet("Box style", boxStyles, boxStyle.coerceIn(0, boxStyles.lastIndex), { i -> vm.setBoxStyle(i) }) { picker = null }
        }
    }
}
