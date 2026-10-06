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
private val WELCOMED = booleanPreferencesKey("welcomed")

/** Something shared into the app from elsewhere, waiting to be saved. */
data class Incoming(val text: String, val subject: String, val images: List<Uri>)

class ArchiveViewModel(app: Application) : AndroidViewModel(app) {
    val incoming = MutableStateFlow<Incoming?>(null)
    fun clearIncoming() { incoming.value = null }
    private val dao = Db.get(app).dao()
    val items: StateFlow<List<Item>> = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shelves = listOf(Shelf("Books", 48, 0), Shelf("Quotes", 112, 2), Shelf("Ideas", 64, 1), Shelf("Memories", 27, 3), Shelf("Movies", 31, 2), Shelf("References", 19, 4))

    private val ds = app.dataStore
    val theme: StateFlow<SbTheme> = ds.data.map { p ->
        Themes[(p[ACC] ?: 0).coerceIn(Themes.indices)].copy(serif = p[SERIF] ?: true, radius = (p[RAD] ?: 14).dp)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Themes[0])
    private fun edit(f: (MutablePreferences) -> Unit) { viewModelScope.launch { ds.edit { f(it) } } }
    val welcomed: StateFlow<Boolean> = ds.data.map { it[WELCOMED] ?: false }.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun dismissWelcome() = edit { it[WELCOMED] = true }
    fun setAccent(i: Int) = edit { it[ACC] = i }
    fun setSerif(b: Boolean) = edit { it[SERIF] = b }
    fun setRadius(dp: Int) = edit { it[RAD] = dp }

    init { viewModelScope.launch { if (dao.count() == 0) sample.forEach { dao.upsert(it) } } }

    /** Copies a picked photo into app storage so it stays available after the picker's temporary access ends. */
    fun importImage(uri: Uri, done: (String) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                val f = File(ctx.filesDir, "img_${System.currentTimeMillis()}.jpg")
                ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                Uri.fromFile(f).toString()
            }
            done(path)
        }
    }
    private fun itemJson(i: Item, img: String?) = org.json.JSONObject().apply {
        put("title", i.title); put("body", i.body); put("kind", i.kind.name); put("collection", i.collection)
        put("tags", org.json.JSONArray(i.tags)); put("layout", i.layout.name); put("color", i.color); put("pinned", i.pinned)
        put("savedAt", i.savedAt); put("font", i.font); put("density", i.density); put("showMeta", i.showMeta)
        put("imageUri", img ?: org.json.JSONObject.NULL); put("canvas", i.canvas)
    }

    /** Zip with archive.json and every photo. Returns the file to share. */
    suspend fun buildBackup(): File = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val out = File(File(ctx.cacheDir, "backups").apply { mkdirs() }, "scrapbook-backup.zip")
        val arr = org.json.JSONArray(); val imgs = mutableSetOf<File>()
        items.value.forEach { i ->
            val f = i.imageUri?.let { File(Uri.parse(it).path ?: "") }?.takeIf { it.exists() }
            if (f != null) imgs += f
            arr.put(itemJson(i, f?.let { "images/${it.name}" }))
        }
        ZipOutputStream(out.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("archive.json")); z.write(arr.toString(2).toByteArray()); z.closeEntry()
            imgs.forEach { f -> z.putNextEntry(ZipEntry("images/${f.name}")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
        }
        out
    }

    /** Adds items from a backup zip, skipping ones already here (same title and date). done(-1) means unreadable. */
    fun restoreBackup(uri: Uri, done: (Int) -> Unit) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                try {
                    val ctx = getApplication<Application>(); val stamp = System.currentTimeMillis(); var json: String? = null
                    ZipInputStream(ctx.contentResolver.openInputStream(uri)).use { z ->
                        var e = z.nextEntry
                        while (e != null) {
                            if (e.name == "archive.json") json = z.readBytes().toString(Charsets.UTF_8)
                            else if (e.name.startsWith("images/") && !e.isDirectory)
                                File(ctx.filesDir, "imp_${stamp}_" + File(e.name).name).outputStream().use { o -> z.copyTo(o) }
                            e = z.nextEntry
                        }
                    }
                    val arr = org.json.JSONArray(json ?: return@withContext -1)
                    val have = items.value.map { it.title to it.savedAt }.toSet(); var added = 0
                    for (k in 0 until arr.length()) {
                        val o = arr.getJSONObject(k); val at = o.getLong("savedAt")
                        if ((o.getString("title") to at) in have) continue
                        val img = o.optString("imageUri", "").takeIf { it.startsWith("images/") }
                            ?.let { Uri.fromFile(File(ctx.filesDir, "imp_${stamp}_" + File(it).name)).toString() }
                        val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                        dao.upsert(Item(title = o.getString("title"), body = o.optString("body"),
                            kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrDefault(Kind.Note),
                            collection = o.optString("collection", "Inbox"), tags = tags,
                            layout = runCatching { Layout.valueOf(o.getString("layout")) }.getOrDefault(Layout.Classic),
                            color = o.optInt("color"), pinned = o.optBoolean("pinned"), savedAt = at, font = o.optInt("font"),
                            density = o.optInt("density", 1), showMeta = o.optBoolean("showMeta", true), imageUri = img, canvas = o.optString("canvas")))
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
                    val buf = ByteArray(65536)
                    val n = c.inputStream.use { it.read(buf) }.coerceAtLeast(0)
                    extractTitle(String(buf, 0, n, Charsets.UTF_8))
                }.getOrNull()
            }
            if (t != null) done(t)
        }
    }

    fun save(item: Item) { viewModelScope.launch { dao.upsert(item) } }
    fun delete(item: Item) { viewModelScope.launch { dao.delete(item) } }
    fun togglePin(item: Item) = save(item.copy(pinned = !item.pinned))
    fun setLayout(id: Long, layout: Layout) { items.value.firstOrNull { it.id == id }?.let { save(it.copy(layout = layout)) } }

    companion object {
        val sample = listOf(
            Item(1, "An idea I had at 2 AM", "A town where every street is named after something forgotten.", Kind.Note, "Ideas", listOf("fiction"), color = 0, pinned = true),
            Item(2, "Collioure, July", "The harbour before anyone woke.", Kind.Image, "Memories", layout = Layout.Gallery, color = 1, pinned = true),
            Item(3, "Movie thoughts", "Slow films ask you to stay.", Kind.Note, "Movies", color = 2),
            Item(4, "Van Gogh references", "Letters, sketches, links.", Kind.Image, "References", color = 3, pinned = true),
            Item(5, "Quote cards", "I dream my painting, and then I paint my dream.", Kind.Quote, "Quotes", layout = Layout.Quote, color = 4),
        )
    }
}
