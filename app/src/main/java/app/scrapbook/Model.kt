package app.scrapbook

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Layout(val label: String) {
    Classic("Classic"), Journal("Journal"), Gallery("Gallery"), TwoColumn("Two Column"),
    Quote("Quote"), Reference("Reference"), Collection("Collection"), Blank("Blank Canvas")
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
)

data class Shelf(val name: String, val count: Int, val color: Int)
