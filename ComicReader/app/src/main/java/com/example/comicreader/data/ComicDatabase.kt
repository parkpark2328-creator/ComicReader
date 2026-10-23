package com.example.comicreader.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "comics")
data class Comic(
    @PrimaryKey val url: String,
    val title: String,
    val coverUrl: String?,
    val imageUrls: String,
    val lastPage: Int = 0,
    val liked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val previousUrl: String? = null,
    val nextUrl: String? = null
) {
    fun pages(): List<String> = imageUrls.split('\n').filter { it.startsWith("https://") }
}

@Dao
interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY updatedAt DESC") fun all(): Flow<List<Comic>>
    @Query("SELECT * FROM comics WHERE liked = 1 ORDER BY updatedAt DESC") fun liked(): Flow<List<Comic>>
    @Query("SELECT * FROM comics WHERE url = :url LIMIT 1") suspend fun get(url: String): Comic?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(comic: Comic)
    @Query("UPDATE comics SET liked = :liked WHERE url = :url") suspend fun setLiked(url: String, liked: Boolean)
    @Query("UPDATE comics SET updatedAt = :time WHERE url = :url") suspend fun markOpened(url: String, time: Long)
    @Query("UPDATE comics SET lastPage = :page, updatedAt = :time WHERE url = :url") suspend fun savePage(url: String, page: Int, time: Long)
    @Query("DELETE FROM comics WHERE url = :url") suspend fun delete(url: String)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comics ADD COLUMN previousUrl TEXT")
        db.execSQL("ALTER TABLE comics ADD COLUMN nextUrl TEXT")
    }
}

@Database(entities = [Comic::class], version = 2, exportSchema = false)
abstract class ComicDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    companion object {
        @Volatile private var INSTANCE: ComicDatabase? = null
        fun get(context: Context): ComicDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, ComicDatabase::class.java, "comic-reader.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
