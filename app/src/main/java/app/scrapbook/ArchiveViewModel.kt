package app.scrapbook

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import app.scrapbook.ui.SbTheme
import app.scrapbook.ui.Themes
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore("settings")
private val ACC = intPreferencesKey("accent")
private val SERIF = booleanPreferencesKey("serif")
private val RAD = intPreferencesKey("radius")
private val DARK = intPreferencesKey("dark")
private val HEADER = intPreferencesKey("header")
private val INBOX = intPreferencesKey("inbox_color")
private val NAV = intPreferencesKey("nav_style")
private val EDITOR = intPreferencesKey("editor_style")
private val WELCOMED = booleanPreferencesKey("welcomed")
private val INBOX_HIDDEN = booleanPreferencesKey("inbox_hidden")
private val GREY_BG = booleanPreferencesKey("grey_bg")
private val HEADER_SIZE = intPreferencesKey("header_size")
private val DEFAULT_GRID = booleanPreferencesKey("default_grid")
private val SORT_TITLE = booleanPreferencesKey("sort_title")
private val SORT_ASC = booleanPreferencesKey("sort_asc")
private val BOX_STYLE = intPreferencesKey("box_style")
private val SHOW_PREVIEW = booleanPreferencesKey("show_preview")
private val SHOW_BOXES = booleanPreferencesKey("show_boxes")
private val LAST_BOX = stringPreferencesKey("last_box")
private val BOX_SEARCH = booleanPreferencesKey("box_search")
private val REOPEN_LAST_BOX = booleanPreferencesKey("reopen_last_box")

/** Something shared into the app from elsewhere, waiting to be saved. */
data class Incoming(val text: String, val subject: String, val images: List<Uri>)

class ArchiveViewModel(app: Application) : AndroidViewModel(app) {
    /** Set by a collection screen so the next new note starts in that collection. */
    var pendingShelf: String? = null
    /** Set by the Home page's quick buttons: the editor opens a new scrap with this text already in it, or goes straight to the photo picker. */
    var pendingBody: String? = null
    var pendingImage = false
    fun takePendingBody(): String? { val s = pendingBody; pendingBody = null; return s }
    fun takePendingImage(): Boolean { val b = pendingImage; pendingImage = false; return b }
    fun takePendingShelf(): String? { val s = pendingShelf; pendingShelf = null; return s }
    val incoming = MutableStateFlow<Incoming?>(null)
    fun clearIncoming() { incoming.value = null }
    private val dao = Db.get(app).dao()
    val items: StateFlow<List<Item>> = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shelves: StateFlow<List<Shelf>> = dao.shelves().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val ds = app.dataStore
    val theme: StateFlow<SbTheme> = ds.data.map { p ->
        Themes[(p[ACC] ?: 0).coerceIn(Themes.indices)].copy(serif = p[SERIF] ?: false, radius = (p[RAD] ?: 24).dp)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Themes[0])
    private fun edit(f: (MutablePreferences) -> Unit) { viewModelScope.launch { ds.edit { f(it) } } }
    val welcomed: StateFlow<Boolean> = ds.data.map { it[WELCOMED] ?: false }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun dismissWelcome() = edit { it[WELCOMED] = true }
    val darkMode: StateFlow<Int> = ds.data.map { it[DARK] ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun setDark(i: Int) = edit { it[DARK] = i }
    /** Main page header: 0 wordmark on the left, 1 wordmark centered, 2 round buttons with a centered wordmark. */
    val headerStyle: StateFlow<Int> = ds.data.map { it[HEADER] ?: 1 }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    fun setHeader(i: Int) = edit { it[HEADER] = i }
    /** Color of the Inbox box: -1 is gray, otherwise an index into the box colors. */
    val inboxColor: StateFlow<Int> = ds.data.map { it[INBOX] ?: -1 }.stateIn(viewModelScope, SharingStarted.Eagerly, -1)
    fun setInboxColor(i: Int) = edit { it[INBOX] = i }
    /** Inbox is built in, so "deleting" it hides its tile. Its scraps stay in All scraps, and the Boxes screen can bring it back. */
    val inboxHidden: StateFlow<Boolean> = ds.data.map { it[INBOX_HIDDEN] ?: false }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setInboxHidden(b: Boolean) = edit { it[INBOX_HIDDEN] = b }
    /** Add button style: 0 is the "New scrap" pill, 1 is a single round menu button (as in Binder): tap for the menu, hold it to pop out the round add button. */
    val navStyle: StateFlow<Int> = ds.data.map { it[NAV] ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun setNav(i: Int) = edit { it[NAV] = i }
    /** Editor toolbar: 0 is the selection bubble (default), 1 is the floating pill. */
    val editorStyle: StateFlow<Int> = ds.data.map { it[EDITOR] ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun setEditorStyle(i: Int) = edit { it[EDITOR] = i }
    /** Light mode page color: false is white (the default), true is the soft grey page. Dark mode ignores it. */
    val greyBg: StateFlow<Boolean> = ds.data.map { it[GREY_BG] ?: false }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setGreyBg(b: Boolean) = edit { it[GREY_BG] = b }
    /** Size of the "scrapbook" title on the main page: 0 compact, 1 regular, 2 large. */
    val headerSize: StateFlow<Int> = ds.data.map { it[HEADER_SIZE] ?: 1 }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    fun setHeaderSize(i: Int) = edit { it[HEADER_SIZE] = i }
    /** The view the main page starts in. The list/grid switch on the page itself only changes it until you leave. */
    val defaultGrid: StateFlow<Boolean> = ds.data.map { it[DEFAULT_GRID] ?: false }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setDefaultGrid(b: Boolean) = edit { it[DEFAULT_GRID] = b }
    /** How the main page is sorted: by title (true) or by date edited (false), and whether the order is reversed (oldest first, Z to A). */
    val sortTitle: StateFlow<Boolean> = ds.data.map { it[SORT_TITLE] ?: false }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setSortTitle(b: Boolean) = edit { it[SORT_TITLE] = b }
    val sortAsc: StateFlow<Boolean> = ds.data.map { it[SORT_ASC] ?: false }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setSortAsc(b: Boolean) = edit { it[SORT_ASC] = b }
    fun setSort(title: Boolean, asc: Boolean) = edit { it[SORT_TITLE] = title; it[SORT_ASC] = asc }
    /** Look of the boxes: an index into BOX_STYLES (0 tabbed, 1 paper stack, 2 tinted tiles, 3 folder tabs, 4 chips, 5 list rows, 6 notebook covers). */
    val boxStyle: StateFlow<Int> = ds.data.map { it[BOX_STYLE] ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun setBoxStyle(i: Int) = edit { it[BOX_STYLE] = i }
    /** Whether lists show a few lines of each scrap under its title. */
    val showPreview: StateFlow<Boolean> = ds.data.map { it[SHOW_PREVIEW] ?: true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setShowPreview(b: Boolean) = edit { it[SHOW_PREVIEW] = b }
    /** Whether the box tiles are shown on the main page. */
    val showBoxes: StateFlow<Boolean> = ds.data.map { it[SHOW_BOXES] ?: true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setShowBoxes(b: Boolean) = edit { it[SHOW_BOXES] = b }
    /** Whether the search bar is shown at the top of a box's screen. On by default. */
    val showBoxSearch: StateFlow<Boolean> = ds.data.map { it[BOX_SEARCH] ?: true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setShowBoxSearch(b: Boolean) = edit { it[BOX_SEARCH] = b }
    /** Whether the app opens in the box you were last in, instead of the main page. On by default. */
    val reopenLastBox: StateFlow<Boolean> = ds.data.map { it[REOPEN_LAST_BOX] ?: false }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setReopenLastBox(b: Boolean) = edit { it[REOPEN_LAST_BOX] = b }
    suspend fun readReopenLastBox(): Boolean = ds.data.first()[REOPEN_LAST_BOX] ?: false
    /** The box that was open when the app was last used. "" means the main page (or All scraps, Pinned and Recent, which are not boxes). */
    suspend fun readLastBox(): String = ds.data.first()[LAST_BOX] ?: ""
    fun setLastBox(name: String) = edit { it[LAST_BOX] = name }
    /** The boxes to open, outermost first, to land in `name` with the same back stack as if you had tapped your way there. Empty if the box is gone. */
    suspend fun boxPath(name: String): List<String> {
        if (name == "Inbox") return if (ds.data.first()[INBOX_HIDDEN] ?: false) emptyList() else listOf("Inbox")
        val all = dao.shelves().first()
        if (all.none { it.name == name }) return emptyList()
        val path = ArrayList<String>(); var cur = name
        while (cur.isNotBlank() && path.size < 20 && cur !in path) { path.add(0, cur); cur = all.firstOrNull { it.name == cur }?.parent ?: "" }
        return path
    }
    fun setAccent(i: Int) = edit { it[ACC] = i }
    fun setSerif(b: Boolean) = edit { it[SERIF] = b }
    fun setRadius(dp: Int) = edit { it[RAD] = dp }

    fun addExamples() {
        viewModelScope.launch {
            listOf(Shelf("Ideas", "Half-formed things worth keeping.", 0), Shelf("Memories", "", 1), Shelf("Movies", "", 2),
                Shelf("References", "", 3), Shelf("Quotes", "Lines I want to remember.", 4)).forEach { dao.upsertShelf(it) }
            sample.forEach { dao.upsert(it.copy(id = 0)) }
        }
    }

    /** Saves a collection. If it was renamed, its notes move to the new name. */
    fun saveShelf(s: Shelf, oldName: String? = null) {
        viewModelScope.launch {
            if (oldName != null && oldName != s.name) { dao.removeShelf(oldName); dao.moveItems(oldName, s.name); dao.moveShelves(oldName, s.name) }
            dao.upsertShelf(s)
        }
    }
    /** Deletes the box and keeps its scraps: they move to Inbox (which reappears if it was hidden). Boxes inside it move up one level. */
    fun deleteShelf(name: String) {
        val hadScraps = items.value.any { it.collection == name }
        val up = shelves.value.firstOrNull { it.name == name }?.parent ?: ""
        viewModelScope.launch { dao.moveItems(name, "Inbox"); dao.moveShelves(name, up); dao.removeShelf(name) }
        if (hadScraps && inboxHidden.value) setInboxHidden(false)
    }
    /** Deletes the box, every box inside it, and every scrap in all of them. Cannot be undone. */
    fun deleteShelfAndScraps(name: String) {
        val names = listOf(name) + shelves.value.descendantsOf(name).map { it.name }
        viewModelScope.launch { names.forEach { dao.deleteItemsIn(it); dao.removeShelf(it) } }
    }
    /** Removes the Inbox box. With deleteScraps, the scraps in it are deleted too (cannot be undone). */
    fun removeInbox(deleteScraps: Boolean) {
        viewModelScope.launch { if (deleteScraps) dao.deleteItemsIn("Inbox") }
        setInboxHidden(true)
    }

    /** Copies a picked photo into app storage so it stays available after the picker's temporary access ends. */
    fun importImage(uri: Uri, done: (String) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                val f = File(ctx.filesDir, "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}.jpg")
                ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                Uri.fromFile(f).toString()
            }
            done(path)
        }
    }
    private fun itemJson(i: Item, imgs: List<String>, cover: String? = null) = org.json.JSONObject().apply {
        put("title", i.title); put("body", i.body); put("kind", i.kind.name); put("collection", i.collection)
        put("tags", org.json.JSONArray(i.tags)); put("layout", i.layout.name); put("color", i.color); put("pinned", i.pinned)
        put("savedAt", i.savedAt); put("font", i.font); put("density", i.density); put("showMeta", i.showMeta)
        put("imageUri", imgs.firstOrNull() ?: org.json.JSONObject.NULL); put("images", org.json.JSONArray(imgs.drop(1))); put("canvas", i.canvas); put("source", i.source); put("accent", i.accent); put("cover", cover ?: org.json.JSONObject.NULL)
    }

    /** Zip with archive.json and every photo. Returns the file to share. */
    suspend fun buildBackup(): File = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val out = File(File(ctx.cacheDir, "backups").apply { mkdirs() }, "scrapbook-backup.zip")
        val arr = org.json.JSONArray(); val imgs = mutableSetOf<File>()
        items.value.forEach { i ->
            val fs = i.allImages().map { File(Uri.parse(it).path ?: "") }.filter { it.exists() }
            imgs += fs
            val cf = i.cover?.let { File(Uri.parse(it).path ?: "") }?.takeIf { it.exists() }
            if (cf != null) imgs += cf
            arr.put(itemJson(i, fs.map { "images/${it.name}" }, cf?.let { "images/${it.name}" }))
        }
        val sarr = org.json.JSONArray()
        shelves.value.forEach { s ->
            val f = s.coverUri?.let { File(Uri.parse(it).path ?: "") }?.takeIf { it.exists() }
            if (f != null) imgs += f
            sarr.put(org.json.JSONObject().apply {
                put("name", s.name); put("description", s.description); put("color", s.color); put("parent", s.parent)
                put("coverUri", f?.let { "images/${it.name}" } ?: org.json.JSONObject.NULL)
            })
        }
        ZipOutputStream(out.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("archive.json")); z.write(arr.toString(2).toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("collections.json")); z.write(sarr.toString(2).toByteArray()); z.closeEntry()
            imgs.forEach { f -> z.putNextEntry(ZipEntry("images/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
        }
        out
    }

    /** Adds items from a backup zip, skipping ones already here (same title and date). done(-1) means unreadable. */
    fun restoreBackup(uri: Uri, done: (Int) -> Unit) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    val ctx = getApplication<Application>(); val stamp = System.currentTimeMillis(); var json: String? = null; var shelvesJson: String? = null
                    ZipInputStream(ctx.contentResolver.openInputStream(uri)).use { z ->
                        var e = z.nextEntry
                        while (e != null) {
                            if (e.name == "archive.json") json = z.readBytes().toString(Charsets.UTF_8)
                            else if (e.name == "collections.json") shelvesJson = z.readBytes().toString(Charsets.UTF_8)
                            else if (e.name.startsWith("images/") && !e.isDirectory)
                                File(ctx.filesDir, "imp_${stamp}_" + File(e.name).name).outputStream().use { o -> z.copyTo(o) }
                            e = z.nextEntry
                        }
                    }
                    shelvesJson?.let { sj ->
                        val a = org.json.JSONArray(sj); val have = shelves.value.map { it.name }.toSet()
                        for (k in 0 until a.length()) {
                            val o = a.getJSONObject(k); val n = o.getString("name"); if (n in have) continue
                            val cv = o.optString("coverUri", "").takeIf { it.startsWith("images/") }
                                ?.let { Uri.fromFile(File(ctx.filesDir, "imp_${stamp}_" + File(it).name)).toString() }
                            dao.upsertShelf(Shelf(n, o.optString("description"), o.optInt("color"), cv, o.optString("parent", "")))
                        }
                    }
                    val arr = org.json.JSONArray(json ?: return@withContext -1)
                    val have = items.value.map { it.title to it.savedAt }.toSet(); var added = 0
                    for (k in 0 until arr.length()) {
                        val o = arr.getJSONObject(k); val at = o.getLong("savedAt")
                        if ((o.getString("title") to at) in have) continue
                        fun imp(p: String) = Uri.fromFile(File(ctx.filesDir, "imp_${stamp}_" + File(p).name)).toString()
                        val first = o.optString("imageUri", "").takeIf { it.startsWith("images/") }?.let { imp(it) }
                        val cov = o.optString("cover", "").takeIf { it.startsWith("images/") }?.let { imp(it) }
                        val extra = o.optJSONArray("images")?.let { a -> (0 until a.length()).map { imp(a.getString(it)) } } ?: emptyList()
                        val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                        dao.upsert(Item(title = o.getString("title"), body = o.optString("body"),
                            kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrDefault(Kind.Note),
                            collection = o.optString("collection", "Inbox"), tags = tags,
                            layout = runCatching { Layout.valueOf(o.getString("layout")) }.getOrDefault(Layout.Classic),
                            color = o.optInt("color"), pinned = o.optBoolean("pinned"), savedAt = at, font = o.optInt("font"),
                            density = o.optInt("density", 1), showMeta = o.optBoolean("showMeta", true), imageUri = first, images = extra.joinToString("\n"), canvas = o.optString("canvas"), source = o.optString("source"), accent = o.optInt("accent"), cover = cov))
                        added++
                    }
                    added
                } catch (e: Exception) { -1 }
            }
            done(n)
        }
    }

    /** Best-effort page title for a shared link. Calls done only when one is found. */
    fun fetchTitle(url: String, done: (String) -> Unit) {
        viewModelScope.launch {
            val t = withContext(Dispatchers.IO) {
                runCatching {
                    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 4000; c.readTimeout = 4000; c.setRequestProperty("User-Agent", "Mozilla/5.0")
                    // A single read() can return early, so fill the buffer (the <title> sits near the top of the page).
                    val html = try {
                        c.inputStream.use { s ->
                            val buf = ByteArray(65536); var n = 0
                            while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                            String(buf, 0, n, Charsets.UTF_8)
                        }
                    } finally { c.disconnect() }
                    extractTitle(html)
                }.getOrNull()
            }
            if (t != null) done(t)
        }
    }

    /** Imports plain-text and Markdown files as notes in Inbox. A leading "# Title" line becomes the title. */
    fun importTexts(uris: List<Uri>, done: (Int) -> Unit) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>(); var added = 0
                for (u in uris) {
                    try {
                        val text = ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readText() } ?: continue
                        val name = ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (c.moveToFirst() && i >= 0) c.getString(i) else null
                        }?.substringBeforeLast('.')
                        val lines = text.lines(); val h = lines.indexOfFirst { it.startsWith("# ") }
                        val title = (if (h >= 0) lines[h].drop(2).trim() else name ?: "Imported scrap").take(80)
                        val body = (if (h >= 0) lines.filterIndexed { i, _ -> i != h } else lines).joinToString("\n").trim()
                        dao.upsert(Item(title = title, body = body, kind = Kind.Note, collection = "Inbox")); added++
                    } catch (e: Exception) { }
                }
                added
            }
            done(n)
        }
    }

    fun save(item: Item) { viewModelScope.launch { dao.upsert(item) } }
    suspend fun saveNow(item: Item): Long = dao.upsert(item)
    fun duplicate(item: Item, done: (Long) -> Unit = {}) {
        viewModelScope.launch { done(dao.upsert(item.copy(id = 0, title = item.title + " copy", pinned = false, savedAt = System.currentTimeMillis()))) }
    }

    /** The last deleted note, kept so the Undo snackbar can bring it back. */
    val deleted = MutableStateFlow<Item?>(null)
    fun delete(item: Item) { viewModelScope.launch { dao.delete(item); deleted.value = item } }
    fun undoDelete() { deleted.value?.let { save(it) }; deleted.value = null }
    fun clearDeleted() { deleted.value = null }
    fun togglePin(item: Item) = save(item.copy(pinned = !item.pinned))
    fun setLayout(id: Long, layout: Layout) { items.value.firstOrNull { it.id == id }?.let { save(it.copy(layout = layout)) } }

    companion object {
        val sample = listOf(
            Item(1, "An idea I had at 2 AM", "A town where every street is named after something forgotten.\n\n## To explore\n- [ ] Sketch the map\n- [x] Pick a name", Kind.Note, "Ideas", listOf("fiction"), color = 0, pinned = true),
            Item(2, "Collioure, July", "The harbour before anyone woke.", Kind.Image, "Memories", layout = Layout.Gallery, color = 1, pinned = true),
            Item(3, "Movie thoughts", "Slow films ask you to stay.", Kind.Note, "Movies", color = 2),
            Item(4, "Van Gogh references", "Letters, sketches, links.", Kind.Image, "References", color = 3, pinned = true),
            Item(5, "Quote cards", "I dream my painting, and then I paint my dream.", Kind.Quote, "Quotes", layout = Layout.Quote, color = 4),
        )
    }
}
