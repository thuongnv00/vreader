package com.kenshin.vreader2.data.source.builtin

import com.kenshin.vreader2.domain.model.*
import com.kenshin.vreader2.domain.source.*
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TruyenFullSource(client: OkHttpClient) : HttpSource(client) {

    override val id: Long = 1L
    override val name: String = "TruyenFull"
    override val lang: String = "vi"
    override val type: MangaType = MangaType.NOVEL
    override val baseUrl: String = "https://truyenfull.vision"

    // ── Danh sách truyện ─────────────────────────────────────────────────────

    override suspend fun fetchPopular(page: Int): MangasPage {
        val doc = fetchDocument("$baseUrl/danh-sach/truyen-hot/trang-$page/")
        return parseMangaList(doc)
    }

    override suspend fun fetchLatestUpdates(page: Int): MangasPage {
        val doc = fetchDocument("$baseUrl/danh-sach/truyen-moi/trang-$page/")
        return parseMangaList(doc)
    }

    override suspend fun fetchSearchManga(
        query: String,
        page: Int,
        filters: FilterList,
    ): MangasPage {
        val url = "$baseUrl/tim-kiem/?tukhoa=${query.encodeForUrl()}&page=$page"
        val doc = fetchDocument(url)
        return parseMangaList(doc)
    }

    private fun parseMangaList(doc: Document): MangasPage {
        // Thêm dòng này để debug
        android.util.Log.d("TruyenFull", "HTML snippet: ${doc.select("div.list-truyen").html().take(500)}")

        val mangas = doc.select("div.row[itemtype]").map { el ->
            parseMangaFromElement(el)
        }
        val hasNext = doc.selectFirst("li.next a") != null
        return MangasPage(mangas, hasNext)
    }

    private fun parseMangaFromElement(el: Element): Manga {
        val anchor   = el.selectFirst("h3.truyen-title a")!!
        val url      = anchor.attr("href").removePrefix(baseUrl)
        val title    = anchor.text()
        val thumbUrl = el.selectFirst("div[data-image]")?.attr("data-image")
        val author   = el.selectFirst("span.author")?.text()
        return Manga(
            id           = "${id}:$url",
            sourceId     = id.toString(),
            url          = url,
            title        = title,
            thumbnailUrl = thumbUrl,
            author       = author,
        )
    }

    // ── Chi tiết truyện ───────────────────────────────────────────────────────

    override suspend fun fetchMangaDetails(manga: Manga): Manga {
        val doc     = fetchDocument("$baseUrl${manga.url}")
        val desc    = doc.selectFirst("div.desc-text")?.text()
        val author  = doc.selectFirst("a[itemprop=author]")?.text()
        val genres  = doc.select("div.info-holder a[itemprop=genre]").map { it.text() }
        val statusText = doc.selectFirst("span.text-success, span.text-primary")?.text() ?: ""
        val status  = when {
            statusText.contains("Hoàn", ignoreCase = true) -> MangaStatus.COMPLETED
            else -> MangaStatus.ONGOING
        }
        return manga.copy(
            description = desc,
            author      = author,
            genres      = genres,
            status      = status,
        )
    }

    // ── Danh sách chương ─────────────────────────────────────────────────────

    override suspend fun fetchChapterList(manga: Manga): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        var page = 1
        while (true) {
            val doc   = fetchDocument("$baseUrl${manga.url}trang-$page/#list-chapter")
            val items = doc.select("ul.list-chapter li a")
            if (items.isEmpty()) break
            items.forEach { anchor ->
                val chUrl  = anchor.attr("href").removePrefix(baseUrl)
                val chName = anchor.text()
                chapters.add(
                    Chapter(
                        id      = "${id}:$chUrl",
                        mangaId = manga.id,
                        url     = chUrl,
                        name    = chName,
                        number  = parseChapterNumber(chName),
                    )
                )
            }
            if (doc.selectFirst("li.next a") == null) break
            page++
        }
        return chapters.sortedByDescending { it.number }
    }

    // ── Nội dung chương ───────────────────────────────────────────────────────

    override suspend fun fetchPageList(chapter: Chapter): List<Page> = emptyList()

    override suspend fun fetchNovelContent(chapter: Chapter): NovelContent {
        val doc = fetchDocument("$baseUrl${chapter.url}")
        doc.select("div.ads, script, .chapter-nav, #chapter-nav").remove()
        val contentEl = doc.selectFirst("div#chapter-c")
            ?: doc.selectFirst("div.chapter-c")
            ?: doc.body()
        return NovelContent(
            chapterId   = chapter.id,
            htmlContent = contentEl.html(),
            textContent = contentEl.text(),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseChapterNumber(name: String): Float {
        val match = Regex(
            "(?:chương|chapter|ch)\\.?\\s*(\\d+(?:\\.\\d+)?)",
            RegexOption.IGNORE_CASE
        ).find(name)
        return match?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    }

    private fun String.encodeForUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}

