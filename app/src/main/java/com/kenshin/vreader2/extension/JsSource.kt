package com.kenshin.vreader2.extension

import com.kenshin.vreader2.domain.model.*
import com.kenshin.vreader2.domain.source.*
import com.kenshin.vreader2.extension.js.JsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

class JsSource(
    private val extension: LoadedExtension,
    private val engine: JsEngine,
) : Source {

    override val id: Long = extension.metadata.source.hashCode().toLong()
    override val name: String = extension.metadata.name
    override val lang: String = extension.metadata.locale.take(2)
    override val baseUrl: String = extension.metadata.source
    override val type: MangaType
        get() = when (extension.metadata.type) {
            "comic" -> MangaType.MANGA
            else -> MangaType.NOVEL
        }

    private fun getScript(name: String): String? = extension.scripts[name]

    private suspend fun runScript(scriptName: String, vararg args: Any?): NativeObject? =
        withContext(Dispatchers.IO) {
            val script = getScript(scriptName) ?: return@withContext null
            engine.execute(script, *args) as? NativeObject
        }

    private fun parseBookList(result: NativeObject?): MangasPage {
        val data = result?.get("data") as? NativeArray ?: return MangasPage(emptyList(), false)
        val next = result.get("data2")?.toString()
        val hasNext = !next.isNullOrEmpty() && next != "null"
        val mangas = (0 until data.length).mapNotNull { i ->
            val item = data[i] as? NativeObject ?: return@mapNotNull null
            val link = item.get("link")?.toString() ?: return@mapNotNull null
            val host = item.get("host")?.toString() ?: baseUrl
            val fullLink = if (link.startsWith("http")) link else "$host$link"
            Manga(
                id           = "${id}:$fullLink",
                sourceId     = id.toString(),
                url          = fullLink,
                title        = item.get("name")?.toString() ?: "",
                thumbnailUrl = item.get("cover")?.toString(),
                description  = item.get("description")?.toString(),
            )
        }
        return MangasPage(mangas, hasNext)
    }

    override suspend fun fetchPopular(page: Int): MangasPage = withContext(Dispatchers.IO) {
        val homeScriptName = extension.pluginScript.home ?: "home"
        val homeScript = getScript(homeScriptName) ?: return@withContext MangasPage(emptyList(), false)
        val homeResult = engine.execute(homeScript) as? NativeObject ?: return@withContext MangasPage(emptyList(), false)
        val tabs = homeResult.get("data") as? NativeArray ?: return@withContext MangasPage(emptyList(), false)
        val firstTab = tabs[0] as? NativeObject ?: return@withContext MangasPage(emptyList(), false)
        val contentScriptName = firstTab.get("script")?.toString() ?: return@withContext MangasPage(emptyList(), false)
        val input = firstTab.get("input")?.toString() ?: ""
        val result = runScript(contentScriptName, input, if (page == 1) null else page.toString())
        parseBookList(result)
    }

    override suspend fun fetchLatestUpdates(page: Int): MangasPage = withContext(Dispatchers.IO) {
        val homeScriptName = extension.pluginScript.home ?: "home"
        val homeScript = getScript(homeScriptName) ?: return@withContext MangasPage(emptyList(), false)
        val homeResult = engine.execute(homeScript) as? NativeObject ?: return@withContext MangasPage(emptyList(), false)
        val tabs = homeResult.get("data") as? NativeArray ?: return@withContext MangasPage(emptyList(), false)
        val tabIndex = if (tabs.length > 1) 1 else 0
        val tab = tabs[tabIndex] as? NativeObject ?: return@withContext MangasPage(emptyList(), false)
        val contentScriptName = tab.get("script")?.toString() ?: return@withContext MangasPage(emptyList(), false)
        val input = tab.get("input")?.toString() ?: ""
        val result = runScript(contentScriptName, input, if (page == 1) null else page.toString())
        parseBookList(result)
    }

    override suspend fun fetchSearchManga(query: String, page: Int, filters: FilterList): MangasPage = withContext(Dispatchers.IO) {
        val scriptName = extension.pluginScript.search ?: return@withContext MangasPage(emptyList(), false)
        val result = runScript(scriptName, query, if (page == 1) null else page.toString())
        parseBookList(result)
    }

    override suspend fun fetchMangaDetails(manga: Manga): Manga = withContext(Dispatchers.IO) {
        val result = runScript(extension.pluginScript.detail, manga.url) ?: return@withContext manga
        val data = result.get("data") as? NativeObject ?: return@withContext manga
        manga.copy(
            title        = data.get("name")?.toString() ?: manga.title,
            thumbnailUrl = data.get("cover")?.toString() ?: manga.thumbnailUrl,
            author       = data.get("author")?.toString(),
            description  = data.get("description")?.toString(),
            status       = if (data.get("ongoing") == true) MangaStatus.ONGOING else MangaStatus.COMPLETED,
        )
    }

    override suspend fun fetchChapterList(manga: Manga): List<Chapter> = withContext(Dispatchers.IO) {
        val tocScript = extension.pluginScript.toc
        val pageScript = extension.pluginScript.page
        val pages = if (pageScript != null) {
            val pageResult = runScript(pageScript, manga.url)
            val pageData = pageResult?.get("data") as? NativeArray
            (0 until (pageData?.length ?: 0)).map { pageData!![it].toString() }
        } else {
            listOf(manga.url)
        }
        val chapters = mutableListOf<Chapter>()
        pages.forEach { pageUrl ->
            val result = runScript(tocScript, pageUrl)
            val data = result?.get("data") as? NativeArray ?: return@forEach
            (0 until data.length).forEach { i ->
                val item = data[i] as? NativeObject ?: return@forEach
                val url = item.get("url")?.toString() ?: return@forEach
                val host = item.get("host")?.toString() ?: baseUrl
                val fullUrl = if (url.startsWith("http")) url else "$host$url"
                chapters.add(Chapter(
                    id      = "${id}:$fullUrl",
                    mangaId = manga.id,
                    url     = fullUrl,
                    name    = item.get("name")?.toString() ?: "Chương ${i + 1}",
                    number  = (chapters.size + 1).toFloat(),
                ))
            }
        }
        chapters.reversed()
    }

    override suspend fun fetchPageList(chapter: Chapter): List<Page> = withContext(Dispatchers.IO) {
        val result = runScript(extension.pluginScript.chap, chapter.url)
        val data = result?.get("data") as? NativeArray ?: return@withContext emptyList()
        (0 until data.length).map { i -> Page(index = i.toInt(), url = data[i].toString()) }
    }

    override suspend fun fetchNovelContent(chapter: Chapter): NovelContent = withContext(Dispatchers.IO) {
        val result = runScript(extension.pluginScript.chap, chapter.url)
        val data = result?.get("data")?.toString() ?: ""
        NovelContent(
            chapterId   = chapter.id,
            htmlContent = data,
            textContent = android.text.Html.fromHtml(data, android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
        )
    }
}