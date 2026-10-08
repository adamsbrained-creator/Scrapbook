package app.scrapbook

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun fromTags(v: List<String>) = v.joinToString("\u001F")
    @TypeConverter fun toTags(v: String) = if (v.isEmpty()) emptyList() else v.split("\u001F")
    @TypeConverter fun fromKind(v: Kind) = v.name
    @TypeConverter fun toKind(v: String) = Kind.valueOf(v)
    @TypeConverter fun fromLayout(v: Layout) = v.name
    @TypeConverter fun toLayout(v: String) = Layout.valueOf(v)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY pinned DESC, savedAt DESC") fun all(): Flow<List<Item>>
    @Upsert suspend fun upsert(item: Item): Long
    @Delete suspend fun delete(item: Item)
    @Query("SELECT * FROM collections ORDER BY name COLLATE NOCASE") fun shelves(): Flow<List<Shelf>>
    @Upsert suspend fun upsertShelf(shelf: Shelf)
    @Query("DELETE FROM collections WHERE name = :name") suspend fun removeShelf(name: String)
    @Query("UPDATE items SET collection = :newName WHERE collection = :oldName") suspend fun moveItems(oldName: String, newName: String)
    @Query("SELECT * FROM items ORDER BY savedAt ASC LIMIT 20") suspend fun oldest(): List<Item>
    @Query("SELECT COUNT(*) FROM items") suspend fun count(): Int
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE items ADD COLUMN canvas TEXT NOT NULL DEFAULT ''") }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS collections (name TEXT NOT NULL, description TEXT NOT NULL, color INTEGER NOT NULL, coverUri TEXT, PRIMARY KEY(name))")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE items ADD COLUMN source TEXT NOT NULL DEFAULT ''") }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE items ADD COLUMN images TEXT NOT NULL DEFAULT ''") }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN accent INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE items ADD COLUMN cover TEXT")
    }
}

@Database(entities = [Item::class, Shelf::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() { abstract fun dao(): ItemDao }

/** One shared database for the app and the widget. */
object Db {
    @Volatile private var inst: AppDb? = null
    fun get(c: android.content.Context): AppDb = inst ?: synchronized(this) {
        inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "scrapbook.db")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { inst = it }
    }
}
