package com.kenshin.vreader2.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "manga")
data class MangaEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val author: String?,
    val description: String?,
    val genres: String,
    val status: String,
    val type: String,
    val inLibrary: Boolean,
    val lastReadAt: Long?,
    val unreadCount: Int,
    val addedToLibraryAt: Long,
)

@Entity(tableName = "chapter")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val url: String,
    val name: String,
    val number: Float,
    val uploadedAt: Long?,
    val isRead: Boolean,
    val readProgress: Int,
    val isDownloaded: Boolean,
    val fetchedAt: Long,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val chapterId: String,
    val mangaId: String,
    val readAt: Long,
    val readProgress: Int,
)

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE inLibrary = 1 ORDER BY lastReadAt DESC")
    fun observeLibrary(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun getMangaById(id: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url")
    suspend fun getMangaBySourceUrl(sourceId: String, url: String): MangaEntity?

    @Upsert
    suspend fun upsertManga(manga: MangaEntity)

    @Query("UPDATE manga SET inLibrary = :inLibrary WHERE id = :id")
    suspend fun setInLibrary(id: String, inLibrary: Boolean)

    @Query("UPDATE manga SET lastReadAt = :time WHERE id = :id")
    suspend fun updateReadAt(id: String, time: Long)

    @Query("UPDATE manga SET unreadCount = :count WHERE id = :id")
    suspend fun updateUnreadCount(id: String, count: Int)

    @Query("SELECT * FROM manga WHERE inLibrary = 1")
    suspend fun getLibraryOnce(): List<MangaEntity>
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapter WHERE mangaId = :mangaId ORDER BY number DESC")
    fun observeChapters(mangaId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter WHERE mangaId = :mangaId ORDER BY number DESC")
    suspend fun getChaptersByManga(mangaId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapter WHERE id = :id")
    suspend fun getChapterById(id: String): ChapterEntity?

    @Upsert
    suspend fun upsertChapters(chapters: List<ChapterEntity>)

    @Query("UPDATE chapter SET isRead = 1, readProgress = :progress WHERE id = :id")
    suspend fun markRead(id: String, progress: Int)

    @Query("UPDATE chapter SET isDownloaded = :downloaded WHERE id = :id")
    suspend fun setDownloaded(id: String, downloaded: Boolean)

    @Query("SELECT COUNT(*) FROM chapter WHERE mangaId = :mangaId AND isRead = 0")
    suspend fun countUnread(mangaId: String): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY readAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(history: HistoryEntity)
}

@Database(
    entities = [MangaEntity::class, ChapterEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VReaderDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun historyDao(): HistoryDao
}