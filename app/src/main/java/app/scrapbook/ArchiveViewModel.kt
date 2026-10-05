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
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore("settings")
private val ACC = intPreferencesKey("accent")
private val SERIF = booleanPreferencesKey("serif")
private val RAD = intPreferencesKey("radius")

class ArchiveViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = Room.databaseBuilder(app, AppDb::class.java, "scrapbook.db").fallbackToDestructiveMigration().build().dao()
    val items: StateFlow<List<Item>> = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shelves = listOf(Shelf("Books", 48, 0), Shelf("Quotes", 112, 2), Shelf("Ideas", 64, 1), Shelf("Memories", 27, 3), Shelf("Movies", 31, 2), Shelf("References", 19, 4))

    private val ds = app.dataStore
    val theme: StateFlow<SbTheme> = ds.data.map { p ->
        Themes[(p[ACC] ?: 0).coerceIn(Themes.indices)].copy(serif = p[SERIF] ?: true, radius = (p[RAD] ?: 14).dp)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Themes[0])
    private fun edit(f: (MutablePreferences) -> Unit) { viewModelScope.launch { ds.edit { f(it) } } }
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
