package app.scrapbook

import androidx.room.*
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
    @Query("SELECT COUNT(*) FROM items") suspend fun count(): Int
}

@Database(entities = [Item::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() { abstract fun dao(): ItemDao }
