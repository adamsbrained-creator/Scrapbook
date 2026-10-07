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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
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
private val MdTransform = VisualTransformation { text ->
    val a = buildAnnotatedString {
        append(text.text); var i = 0
        text.text.split("\n").forEach { line ->
            val st: SpanStyle? = when {
                line.startsWith("## ") -> SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                line.startsWith("# ") -> SpanStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold)
                line.startsWith("> ") -> SpanStyle(fontStyle = FontStyle.Italic, color = Sb.mute)
                line.startsWith("- [x] ") -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = Sb.mute)
                line == "---" -> SpanStyle(color = Sb.mute)
                else -> null
            }
            if (st != null && line.isNotEmpty()) addStyle(st, i, i + line.length)
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
private fun Fmt(label: String, desc: String, onClick: () -> Unit) {
    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.defaultMinSize(minWidth = 40.dp).clip(RoundedCornerShape(10.dp)).background(Sb.line.copy(alpha = .6f))
            .tap(desc, onClick).padding(horizontal = 10.dp, vertical = 8.dp))
}

/** A normal notes editor: autosaves as you type, formatting toolbar, lists continue on Enter. id == 0 starts a new note. */
@Composable
fun EditorScreen(vm: ArchiveViewModel, nav: NavController, id: Long) {
    val t = LocalSb.current; val list by vm.items.collectAsState(); val scope = rememberCoroutineScope()
    val existing = remember(id) { vm.items.value.firstOrNull { it.id == id } }
    var curId by remember(id) { mutableLongStateOf(id) }
    var title by remember(id) { mutableStateOf(existing?.title ?: "") }
    var body by remember(id) { mutableStateOf(TextFieldValue(existing?.body ?: "")) }
    var color by remember(id) { mutableIntStateOf(existing?.color ?: 0) }
    var kind by remember(id) { mutableStateOf(existing?.kind ?: Kind.Note) }
    var shelf by remember(id) { mutableStateOf(existing?.collection ?: "Inbox") }
    var tags by remember(id) { mutableStateOf(existing?.tags?.joinToString(", ") ?: "") }
    var imgs by remember(id) { mutableStateOf(existing?.allImages() ?: emptyList()) }
    var source by remember(id) { mutableStateOf(existing?.source ?: "") }
    var more by remember { mutableStateOf(false) }
    val live = list.firstOrNull { it.id == curId }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris -> uris.forEach { u -> vm.importImage(u) { imgs = imgs + it } } }

    fun draft(): Item? {
        if (title.isBlank() && body.text.isBlank() && imgs.isEmpty()) return null
        val firstLine = body.text.lineSequence().firstOrNull { it.isNotBlank() }?.replace(Regex("^(#{1,2} |> |- \\[[ x]\\] |- )"), "")?.take(50)
        val base = live ?: existing ?: Item(title = "", body = "", kind = kind, collection = shelf)
        return base.copy(id = curId, title = title.ifBlank { firstLine ?: "Untitled" }, body = body.text, kind = kind, collection = shelf,
            color = color, imageUri = imgs.firstOrNull(), images = imgs.drop(1).joinToString("\n"), source = source, tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }, savedAt = System.currentTimeMillis())
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
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).background(Sb.notes[color % 5].copy(alpha = .4f)).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), Arrangement.SpaceBetween) {
                Text("‹ Done", fontSize = 13.sp, color = t.acc, fontWeight = FontWeight.SemiBold, modifier = Modifier.link("Save and go back") { leave() })
                if (live != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (live.pinned) "Unpin" else "Pin", fontSize = 13.sp, modifier = Modifier.link("Pin or unpin") { vm.togglePin(live) })
                    Text("Layout", fontSize = 13.sp, modifier = Modifier.link("Change layout") { nav.navigate("layouts/${live.id}") })
                    Text("Delete", fontSize = 13.sp, modifier = Modifier.link("Delete note") { vm.delete(live); nav.popBackStack("home", false) })
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
                Field(title, { title = it }, "Title", 26.sp, headFont(), FontWeight.SemiBold)
                if (kind == Kind.Quote) Field(source, { source = it }, "Source: who said it, or where it is from", 13.sp, FontFamily.SansSerif)
                if (imgs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
                        imgs.forEach { u ->
                            Box {
                                ItemImage(Item(title = "", body = "", kind = kind, collection = shelf, imageUri = u, color = color), Modifier.size(140.dp, 110.dp))
                                Text("✕", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                    .clip(CircleShape).background(Color(0x99000000)).tap("Remove image") { imgs = imgs - u }.padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                BasicTextField(body, { body = continueList(body, it) }, Modifier.fillMaxWidth().heightIn(min = 300.dp),
                    textStyle = TextStyle(fontSize = 15.sp, fontFamily = LocalFonts.current.first, color = Sb.ink, lineHeight = 24.sp),
                    cursorBrush = SolidColor(t.acc), visualTransformation = MdTransform,
                    decorationBox = { inner -> Box { if (body.text.isEmpty()) Text("Write something…", color = Sb.mute, fontSize = 15.sp); inner() } })
            }
            Column(Modifier.fillMaxWidth().background(Sb.card).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (more) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sb.notes.forEachIndexed { i, c ->
                            Box(Modifier.size(24.dp).clip(CircleShape).background(c).border(if (i == color % 5) 2.dp else 1.dp, if (i == color % 5) Sb.ink else Sb.line, CircleShape)
                                .tap(listOf("Butter", "Sage", "Blue", "Rose", "Lilac")[i]) { color = i })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(5.dp)) {
                        Kind.entries.forEach { Chip(it.name, kind == it) { kind = it } }
                        Chip("Inbox", shelf == "Inbox") { shelf = "Inbox" }
                        vm.shelves.value.forEach { Chip(it.name, shelf == it.name) { shelf = it.name } }
                    }
                    Field(tags, { tags = it }, "Tags, separated by commas", 12.sp, FontFamily.SansSerif)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp)) {
                    Fmt("H1", "Heading") { body = body.toggleLine("# ") }
                    Fmt("H2", "Subheading") { body = body.toggleLine("## ") }
                    Fmt("❝", "Quote") { body = body.toggleLine("> ") }
                    Fmt("•", "Bullet list") { body = body.toggleLine("- ") }
                    Fmt("☑", "Checklist") { body = body.toggleLine("- [ ] ") }
                    Fmt("—", "Divider") { body = body.insertDivider() }
                    Fmt("▢", "Add image") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Fmt("⋯", "More options") { more = !more }
                }
            }
        }
    }
}
