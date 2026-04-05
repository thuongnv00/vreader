package com.kenshin.vreader2.data.source

import com.kenshin.vreader2.domain.source.Source
import com.kenshin.vreader2.extension.ExtensionLoader
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceManager @Inject constructor(
    private val client: OkHttpClient,
    private val extensionLoader: ExtensionLoader,
) {
    private var cachedSources: List<Source>? = null

    /**
     * Lấy tất cả các nguồn truyện (Extensions).
     * Sử dụng cache để tránh việc nạp đi nạp lại từ ổ cứng, giúp ID ổn định.
     */
    fun getAll(): List<Source> {
        val cached = cachedSources
        if (cached != null) return cached

        val jsExtensions = extensionLoader.loadAll().map { ext ->
            extensionLoader.createJsSource(ext)
        }
        
        android.util.Log.d("SourceManager", "Nạp thành công ${jsExtensions.size} extensions")
        cachedSources = jsExtensions
        return jsExtensions
    }

    /**
     * Tìm nguồn truyện theo ID.
     */
    fun get(sourceId: Long): Source? =
        getAll().find { it.id == sourceId }

    /**
     * Xóa cache để nạp lại danh sách extension (ví dụ sau khi cài thêm mới).
     */
    fun refresh() {
        cachedSources = null
        android.util.Log.d("SourceManager", "Đã xóa cache Extensions")
    }

    fun getExtensionsPath(): String = extensionLoader.getExtensionsPath()
}