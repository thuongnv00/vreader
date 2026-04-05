package com.kenshin.vreader2.domain.source

import com.kenshin.vreader2.domain.model.Chapter
import com.kenshin.vreader2.domain.model.Manga
import com.kenshin.vreader2.domain.model.MangaType
import com.kenshin.vreader2.domain.model.NovelContent
import com.kenshin.vreader2.domain.model.Page

data class MangasPage(
    val mangas: List<Manga>,
    val hasNextPage: Boolean,
)

data class FilterList(val filters: List<Filter<*>> = emptyList())

sealed class Filter<T>(val name: String, var state: T) {
    class Text(name: String) : Filter<String>(name, "")
    class Select(name: String, val values: Array<String>) : Filter<Int>(name, 0)
    class CheckBox(name: String) : Filter<Boolean>(name, false)
}

interface Source {
    val id: Long
    val name: String
    val lang: String
    val type: MangaType get() = MangaType.MANGA
    val baseUrl: String

    suspend fun fetchPopular(page: Int): MangasPage
    suspend fun fetchLatestUpdates(page: Int): MangasPage
    suspend fun fetchSearchManga(
        query: String,
        page: Int,
        filters: FilterList = FilterList(),
    ): MangasPage

    suspend fun fetchMangaDetails(manga: Manga): Manga
    suspend fun fetchChapterList(manga: Manga): List<Chapter>
    suspend fun fetchPageList(chapter: Chapter): List<Page>
    suspend fun fetchNovelContent(chapter: Chapter): NovelContent

    fun getFilterList(): FilterList = FilterList()
}

abstract class HttpSource(
    protected val client: okhttp3.OkHttpClient,
) : Source {

    protected suspend fun fetchDocument(url: String): org.jsoup.nodes.Document {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = client.newCall(
                okhttp3.Request.Builder().url(url).build()
            ).execute()
            org.jsoup.Jsoup.parse(response.body!!.string(), url)
        }
    }

    protected fun String.toAbsoluteUrl(): String =
        if (startsWith("http")) this else "$baseUrl$this"
}

