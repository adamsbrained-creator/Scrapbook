package app.scrapbook

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Layout(val label: String) {
    Classic("Classic"), Journal("Journal"), Gallery("Gallery"), TwoColumn("Two Column"),
    Quote("Quote"), Reference("Reference"), Collection("Box"), Blank("Blank Canvas")
}

enum class Kind { Note, Image, Quote, Link, Voice }

/** Content lives on the item; layout is only a view of it, so switching layouts never loses anything. */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val body: String, val kind: Kind, val collection: String,
    val tags: List<String> = emptyList(), val layout: Layout = Layout.Classic,
    val color: Int = 0, val pinned: Boolean = false, val savedAt: Long = System.currentTimeMillis(),
    val font: Int = 0, val density: Int = 1, val showMeta: Boolean = true,
    val imageUri: String? = null,
    val canvas: String = "",
    val source: String = "",
    val images: String = "",
    val accent: Int = 0, val cover: String? = null,
)

/** A user-made collection. Notes join it by name (Item.collection). "Inbox" and "All" are built in. A box can sit inside another box: parent is that box's name ("" = main page). */
@Entity(tableName = "collections")
data class Shelf(@PrimaryKey val name: String, val description: String = "", val color: Int = 0, val coverUri: String? = null, val parent: String = "")

fun itemsLabel(n: Int) = if (n == 1) "1 item" else "$n items"

/** Main image first, then any extra images added to the note. */
fun Item.allImages(): List<String> = listOfNotNull(imageUri) + images.split("\n").filter { it.isNotBlank() }

/** What a type is called on screen: a plain note is a "Scrap". */
val Kind.label: String get() = if (this == Kind.Note) "Scrap" else name

/** Boxes that show on the main page: no parent, or a parent that no longer exists (so nothing can get lost). */
fun List<Shelf>.topLevel(): List<Shelf> { val names = map { it.name }.toSet(); return filter { it.parent.isBlank() || it.parent !in names } }

/** The boxes directly inside the box called name. */
fun List<Shelf>.childrenOf(name: String): List<Shelf> = filter { it.parent == name && it.name != name }

/** Every box inside name, at any depth (not including name itself). */
fun List<Shelf>.descendantsOf(name: String): List<Shelf> {
    val out = mutableListOf<Shelf>(); val seen = mutableSetOf(name); val queue = ArrayDeque(listOf(name))
    while (queue.isNotEmpty()) { val n = queue.removeFirst(); childrenOf(n).forEach { if (seen.add(it.name)) { out += it; queue += it.name } } }
    return out
}
