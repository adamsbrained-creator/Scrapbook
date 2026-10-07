package app.scrapbook.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.scrapbook.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Field(v: String, onChange: (String) -> Unit, hint: String, size: TextUnit, font: FontFamily, weight: FontWeight = FontWeight.Normal) {
    BasicTextField(v, onChange, Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontSize = size, fontFamily = font, fontWeight = weight, color = Sb.ink, lineHeight = size * 1.4f),
        cursorBrush = SolidColor(LocalSb.current.acc),
        decorationBox = { inner -> Box { if (v.isEmpty()) Text(hint, color = Sb.mute, fontSize = size, fontFamily = font, fontWeight = weight); inner() } })
}

/** Styles formatting lines as you type (text and cursor positions are unchanged). */
private fun mdTransform(acc: Color) = VisualTransformation { text ->
    val a = buildAnnotatedString {
        append(text.text); var i = 0
        text.text.split("\n").forEach { line ->
            val st: SpanStyle? = when {
                line.startsWith("## ") -> SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                line.startsWith("# ") -> SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
                line.startsWith("> ") -> SpanStyle(fontStyle = FontStyle.Italic, color = Sb.mute)
                line.startsWith("- [x] ") -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = Sb.mute)
                line == "---" -> SpanStyle(color = Sb.mute)
                else -> null
            }
            if (st != null && line.isNotEmpty()) addStyle(st, i, i + line.length)
            if (line.startsWith("- [ ] ") || line.startsWith("- [x] ")) addStyle(SpanStyle(color = acc, fontWeight = FontWeight.Bold), i, i + 5)
            else if (line.startsWith("- ")) addStyle(SpanStyle(color = acc, fontWeight = FontWeight.Bold), i, i + 1)
            i += line.length + 1
        }
    }
    TransformedText(a, OffsetMapping.Identity)
}

private val LINE_PREFIXES = listOf("## ", "# ", "> ", "- [x] ", "- [ ] ", "- ")

/** Adds, switches or removes a line prefix (heading, quote, bullet, checklist) on the cursor's line. */
private fun TextFieldValue.toggleLine(prefix: String): TextFieldValue {
    val s = text; val pos = selection.start.coerceIn(0, s.length)
    val ls = s.lastIndexOf('\n', pos - 1) + 1
    val le = s.indexOf('\n', pos).let { if (it < 0) s.length else it }
    val line = s.substring(ls, le)
    val cur = LINE_PREFIXES.firstOrNull { line.startsWith(it) }
    val stripped = if (cur != null) line.removePrefix(cur) else line
    val newLine = if (cur == prefix) stripped else prefix + stripped
    val d = newLine.length - line.length
    return copy(text = s.substring(0, ls) + newLine + s.substring(le),
        selection = TextRange((selection.start + d).coerceAtLeast(ls), (selection.end + d).coerceAtLeast(ls)))
}

private fun TextFieldValue.insertDivider(): TextFieldValue {
    val pos = selection.start.coerceIn(0, text.length); val before = text.substring(0, pos)
    val ins = (if (before.isEmpty() || before.endsWith("\n")) "" else "\n") + "---\n"
    return copy(text = before + ins + text.substring(pos), selection = TextRange(pos + ins.length))
}

/** Enter inside a list continues it; Enter on an empty item leaves the list. */
private fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.text.length != old.text.length + 1 || new.selection.start == 0 || new.text[new.selection.start - 1] != '\n') return new
    val s = new.text; val nl = new.selection.start - 1
    val ls = s.lastIndexOf('\n', nl - 1) + 1; val line = s.substring(ls, nl)
    val prefix = listOf("- [x] ", "- [ ] ", "- ").firstOrNull { line.startsWith(it) } ?: return new
    if (line.length == prefix.length) return new.copy(text = s.substring(0, ls) + s.substring(nl), selection = TextRange(ls + 1))
    val cont = if (prefix == "- [x] ") "- [ ] " else prefix
    return new.copy(text = s.substring(0, nl + 1) + cont + s.substring(nl + 1), selection = TextRange(nl + 1 + cont.length))
}

@Composable
private fun TbText(label: String, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).tap(desc, onClick), Alignment.Center) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Sb.ink) }
}

@Composable
private fun TbIcon(kind: String, desc: String, color: Color = Sb.ink, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).tap(desc, onClick), Alignment.Center) { Ic(kind, 24.dp, color) }
}

/** A photo as a taped polaroid; the cross removes it. */
@Composable
private fun Polaroid(uri: String, kind: Kind, i: Int, onRemove: () -> Unit) {
    Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp).rotate(if (i % 2 == 0) -3f else 3f)) {
        Box(Modifier.shadow(6.dp, RoundedCornerShape(8.dp)).background(Color.White, RoundedCornerShape(8.dp)).padding(5.dp)) {
            ItemImage(Item(title = "", body = "", kind = kind, collection = "", imageUri = uri), Modifier.size(150.dp, 120.dp), 4.dp)
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-8).dp).size(56.dp, 16.dp).rotate(-2f).background(Color(0x77A8A8A8)))
        Text("✕", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            .clip(CircleShape).background(Color(0x99000000)).tap("Remove image") { onRemove() }.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

/** A normal notes editor: autosaves as you type, formatting toolbar, lists continue on Enter, undo and redo. id == 0 starts a new note. */
@Composable
fun EditorScreen(vm: ArchiveViewModel, nav: NavController, id: Long) {
    val t = LocalSb.current; val list by vm.items.collectAsState(); val scope = rememberCoroutineScope()
    val existing = remember(id) { vm.items.value.firstOrNull { it.id == id } }
    var curId by remember(id) { mutableLongStateOf(id) }
    var title by remember(id) { mutableStateOf(existing?.title ?: "") }
    var body by remember(id) { mutableStateOf(TextFieldValue(existing?.body ?: "")) }
    var color by remember(id) { mutableIntStateOf(existing?.color ?: 0) }
    var kind by remember(id) { mutableStateOf(existing?.kind ?: Kind.Note) }
    var shelf by remember(id) { mutableStateOf(existing?.collection ?: vm.takePendingShelf() ?: "Inbox") }
    var tags by remember(id) { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var imgs by remember(id) { mutableStateOf(existing?.allImages() ?: emptyList()) }
    var source by remember(id) { mutableStateOf(existing?.source ?: "") }
    var more by remember { mutableStateOf(false) }; var menu by remember { mutableStateOf(false) }
    val undo = remember { ArrayDeque<TextFieldValue>() }; val redo = remember { ArrayDeque<TextFieldValue>() }
    var lastPush by remember { mutableLongStateOf(0L) }
    val md = remember(t.acc) { mdTransform(t.acc) }
    val live = list.firstOrNull { it.id == curId }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris -> uris.forEach { u -> vm.importImage(u) { imgs = imgs + it } } }
    fun addImage() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    /** Every body change goes through here so undo and redo see it. Quick typing is grouped into one step. */
    fun edit(new: TextFieldValue) {
        val next = continueList(body, new)
        if (next.text != body.text) {
            val now = System.currentTimeMillis()
            if (now - lastPush > 700 || undo.isEmpty()) { undo.addLast(body); if (undo.size > 100) undo.removeFirst() }
            lastPush = now; redo.clear()
        }
        body = next
    }

    fun draft(): Item? {
        if (title.isBlank() && body.text.isBlank() && imgs.isEmpty()) return null
        val firstLine = body.text.lineSequence().firstOrNull { it.isNotBlank() }?.replace(Regex("^(#{1,2} |> |- \\[[ x]\\] |- )"), "")?.take(50)
        val base = live ?: existing ?: Item(title = "", body = "", kind = kind, collection = shelf)
        return base.copy(id = curId, title = title.ifBlank { firstLine ?: "Untitled" }, body = body.text, kind = kind, collection = shelf,
            color = color, imageUri = imgs.firstOrNull(), images = imgs.drop(1).joinToString("\n"), source = source,
            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }, savedAt = System.currentTimeMillis())
    }
    suspend fun flush() {
        val d = draft() ?: return
        if (live != null && d.copy(savedAt = live.savedAt) == live) return
        val r = vm.saveNow(d); if (curId == 0L) curId = r
    }
    LaunchedEffect(title, body.text, color, kind, shelf, tags, imgs, source) { delay(600); flush() }
    fun leave() { scope.launch { flush(); nav.popBackStack() } }
    BackHandler { leave() }

    Scaffold(containerColor = t.paper) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).background(pageTint(color) ?: Color.Transparent).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).tap("Save and go back") { leave() }, Alignment.Center) { Ic("back", 28.dp, Sb.ink) }
                BasicTextField(title, { title = it.replace("\n", " ") }, Modifier.weight(1f).padding(horizontal = 4.dp), singleLine = true,
                    textStyle = TextStyle(fontSize = 22.sp, fontFamily = headFont(), fontWeight = FontWeight.Medium, color = Sb.ink),
                    cursorBrush = SolidColor(t.acc),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (title.isEmpty()) Text("Title", color = Sb.mute.copy(alpha = .6f), fontSize = 22.sp, fontFamily = headFont(), fontWeight = FontWeight.Medium)
                            inner()
                        }
                    })
                Box(Modifier.size(44.dp).tap("Reading view") { scope.launch { flush(); if (curId != 0L) nav.navigate("item/$curId") } }, Alignment.Center) { Ic("book", 24.dp, Sb.ink) }
                Box(Modifier.size(44.dp).tap("Add image") { addImage() }, Alignment.Center) { Ic("plus", 26.dp, Sb.ink) }
                Box {
                    Box(Modifier.size(44.dp).tap("More options") { menu = true }, Alignment.Center) { Ic("more", 24.dp, Sb.ink) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (more) "Hide details" else "Details: color, collection, tags") }, onClick = { menu = false; more = !more })
                        if (live != null) {
                            DropdownMenuItem(text = { Text(if (live.pinned) "Unpin" else "Pin") }, onClick = { menu = false; vm.togglePin(live) })
                            DropdownMenuItem(text = { Text("Change layout") }, onClick = { menu = false; nav.navigate("layouts/${live.id}") })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; vm.delete(live); nav.popBackStack("home", false) })
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Sb.line))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
                if (kind == Kind.Quote) { Field(source, { source = it }, "Source: who said it, or where it is from", 14.sp, headFont()); Spacer(Modifier.height(10.dp)) }
                if (imgs.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        imgs.forEachIndexed { i, u -> Polaroid(u, kind, i) { imgs = imgs - u } }
                    }
                }
                BasicTextField(body, { edit(it) }, Modifier.fillMaxWidth().heightIn(min = 320.dp),
                    textStyle = TextStyle(fontSize = 17.sp, fontFamily = headFont(), color = Sb.ink, lineHeight = 27.sp),
                    cursorBrush = SolidColor(t.acc), visualTransformation = md,
                    decorationBox = { inner -> Box { if (body.text.isEmpty()) Text("Write something…", color = Sb.mute, fontSize = 17.sp, fontFamily = headFont()); inner() } })
            }
            Column(Modifier.fillMaxWidth().background(Sb.card)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Sb.line))
                if (more) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ColorDots(color) { color = it }
                        Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(5.dp)) {
                            Kind.entries.forEach { Chip(it.name, kind == it) { kind = it } }
                            Chip("Inbox", shelf == "Inbox") { shelf = "Inbox" }
                            vm.shelves.value.forEach { Chip(it.name, shelf == it.name) { shelf = it.name } }
                        }
                        InputBox { Field(tags, { tags = it }, "Tags, separated by commas", 15.sp, headFont()) }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TbText("H1", "Heading") { edit(body.toggleLine("# ")) }
                    TbText("H2", "Subheading") { edit(body.toggleLine("## ")) }
                    TbText("❝", "Quote") { edit(body.toggleLine("> ")) }
                    TbText("•", "Bullet list") { edit(body.toggleLine("- ")) }
                    TbIcon("check", "Checklist") { edit(body.toggleLine("- [ ] ")) }
                    TbText("—", "Divider") { edit(body.insertDivider()) }
                    TbIcon("image", "Add image", t.acc) { addImage() }
                    TbIcon("undo", "Undo", if (undo.isNotEmpty()) Sb.ink else Sb.mute.copy(alpha = .4f)) { if (undo.isNotEmpty()) { redo.addLast(body); body = undo.removeLast() } }
                    TbIcon("redo", "Redo", if (redo.isNotEmpty()) Sb.ink else Sb.mute.copy(alpha = .4f)) { if (redo.isNotEmpty()) { undo.addLast(body); body = redo.removeLast() } }
                }
            }
        }
    }
}
