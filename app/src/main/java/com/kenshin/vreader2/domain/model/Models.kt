package com.kenshin.vreader2.domain.model

data class Manga(
    val id: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val type: MangaType = MangaType.MANGA,
    val inLibrary: Boolean = false,
    val lastReadAt: Long? = null,
    val unreadCount: Int = 0,
)

enum class MangaStatus { UNKNOWN, ONGOING, COMPLETED, HIATUS, DROPPED }
enum class MangaType   { MANGA, NOVEL }

data class Chapter(
    val id: String,
    val mangaId: String,
    val url: String,
    val name: String,
    val number: Float,
    val uploadedAt: Long? = null,
    val isRead: Boolean = false,
    val readProgress: Int = 0,
    val isDownloaded: Boolean = false,
)

data class Page(
    val index: Int,
    val url: String,
    val imageUrl: String? = null,
)

data class NovelContent(
    val chapterId: String,
    val htmlContent: String,
    val textContent: String,
)

data class ExtensionInfo(
    val id: String,
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val iconUrl: String?,
    val isInstalled: Boolean,
    val hasUpdate: Boolean,
)

