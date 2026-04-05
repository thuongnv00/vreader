package com.kenshin.vreader2.data.repository

import com.kenshin.vreader2.data.local.db.*
import com.kenshin.vreader2.data.source.SourceManager
import com.kenshin.vreader2.domain.model.*
import com.kenshin.vreader2.domain.source.FilterList
import com.kenshin.vreader2.domain.source.MangasPage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaRepository @Inject constructor(
    private val sourceManager: SourceManager,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val gson: Gson,
) {
    // ── Library ───────────────────────────────────────────────────────────────

    fun observeLibrary(): Flow<List<Manga>> =
        mangaDao.observeLibrary().map { it.map { e -> e.toDomain() } }

    suspend fun getLibraryOnce(): List<Manga> =
        mangaDao.getLibraryOnce().map { it.toDomain() }

    suspend fun toggleLibrary(mangaId: String, inLibrary: Boolean) {
        mangaDao.setInLibrary(mangaId, inLibrary)
    }

    // ── Browse ────────────────────────────────────────────────────────────────

    suspend fun getPopular(sourceId: Long, page: Int): MangasPage {
        val source = sourceManager.get(sourceId) ?: error("Source $sourceId not found")
        return source.fetchPopular(page)
    }

    suspend fun getLatest(sourceId: Long, page: Int): MangasPage {
        val source = sourceManager.get(sourceId) ?: error("Source $sourceId not found")
        return source.fetchLatestUpdates(page)
    }

    suspend fun search(sourceId: Long, query: String, page: Int): MangasPage {
        val source = sourceManager.get(sourceId) ?: error("Source $sourceId not found")
        return source.fetchSearchManga(query, page, FilterList())
    }

    // ── Manga detail ──────────────────────────────────────────────────────────

    suspend fun getMangaDetails(manga: Manga): Manga {
        val source = sourceManager.get(manga.sourceId.toLong())
            ?: error("Source not found")
        val detail = source.fetchMangaDetails(manga)
        mangaDao.upsertManga(detail.toEntity())
        return detail
    }

    suspend fun getMangaById(id: String): Manga? =
        mangaDao.getMangaById(id)?.toDomain()

    // ── Chapters ──────────────────────────────────────────────────────────────

    fun observeChapters(mangaId: String): Flow<List<Chapter>> =
        chapterDao.observeChapters(mangaId).map { it.map { e -> e.toDomain() } }

    suspend fun getChaptersByManga(mangaId: String): List<Chapter> =
        chapterDao.getChaptersByManga(mangaId).map { it.toDomain() }

    suspend fun getChapterById(id: String): Chapter? =
        chapterDao.getChapterById(id)?.toDomain()

    suspend fun fetchAndSaveChapters(manga: Manga): List<Chapter> {
        val source = sourceManager.get(manga.sourceId.toLong())
            ?: error("Source not found")
        val chapters = source.fetchChapterList(manga)
        chapterDao.upsertChapters(chapters.map { it.toEntity() })
        mangaDao.updateUnreadCount(manga.id, chapters.count { !it.isRead })
        return chapters
    }

    suspend fun markChapterRead(chapterId: String, progress: Int) {
        chapterDao.markRead(chapterId, progress)
        val chapter = chapterDao.getChapterById(chapterId) ?: return
        mangaDao.updateReadAt(chapter.mangaId, System.currentTimeMillis())
    }

    // ── Reader ────────────────────────────────────────────────────────────────

    suspend fun getPageList(chapter: Chapter): List<Page> {
        val source = sourceManager.get(chapter.id.substringBefore(":").toLong())
            ?: error("Source not found")
        return source.fetchPageList(chapter)
    }

    suspend fun getNovelContent(chapter: Chapter): NovelContent {
        val source = sourceManager.get(chapter.id.substringBefore(":").toLong())
            ?: error("Source not found")
        return source.fetchNovelContent(chapter)
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun MangaEntity.toDomain(): Manga = Manga(
        id           = id,
        sourceId     = sourceId,
        url          = url,
        title        = title,
        thumbnailUrl = thumbnailUrl,
        author       = author,
        description  = description,
        genres       = gson.fromJson(genres, object : TypeToken<List<String>>() {}.type),
        status       = MangaStatus.valueOf(status),
        type         = MangaType.valueOf(type),
        inLibrary    = inLibrary,
        lastReadAt   = lastReadAt,
        unreadCount  = unreadCount,
    )

    private fun Manga.toEntity(): MangaEntity = MangaEntity(
        id               = id,
        sourceId         = sourceId,
        url              = url,
        title            = title,
        thumbnailUrl     = thumbnailUrl,
        author           = author,
        description      = description,
        genres           = gson.toJson(genres),
        status           = status.name,
        type             = type.name,
        inLibrary        = inLibrary,
        lastReadAt       = lastReadAt,
        unreadCount      = unreadCount,
        addedToLibraryAt = System.currentTimeMillis(),
    )

    private fun ChapterEntity.toDomain(): Chapter = Chapter(
        id           = id,
        mangaId      = mangaId,
        url          = url,
        name         = name,
        number       = number,
        uploadedAt   = uploadedAt,
        isRead       = isRead,
        readProgress = readProgress,
        isDownloaded = isDownloaded,
    )

    private fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
        id           = id,
        mangaId      = mangaId,
        url          = url,
        name         = name,
        number       = number,
        uploadedAt   = uploadedAt,
        isRead       = isRead,
        readProgress = readProgress,
        isDownloaded = isDownloaded,
        fetchedAt    = System.currentTimeMillis(),
    )
}
