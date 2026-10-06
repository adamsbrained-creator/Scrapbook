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
    @Query("SELECT * FROM items ORDER BY savedAt ASC LIMIT 20") suspend fun oldest(): List<Item>
    @Query("SELECT COUNT(*) FROM items") suspend fun count(): Int
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE items ADD COLUMN canvas TEXT NOT NULL DEFAULT ''") }
}

@Database(entities = [Item::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() { abstract fun dao(): ItemDao }

/** One shared database for the app and the widget. */
object Db {
    @Volatile private var inst: AppDb? = null
    fun get(c: android.content.Context): AppDb = inst ?: synchronized(this) {
        inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "scrapbook.db")
            .addMigrations(MIGRATION_3_4).fallbackToDestructiveMigration().build().also { inst = it }
    }
}
