package com.kenshin.vreader2.data.source

import com.kenshin.vreader2.data.source.builtin.TruyenFullSource
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
    private val builtInSources: List<Source> by lazy {
        listOf(
            // TruyenFullSource(client),
        )
    }

    fun getAll(): List<Source> {
        val jsExtensions = extensionLoader.loadAll().map { ext ->
            extensionLoader.createJsSource(ext)
        }
        android.util.Log.d("SourceManager", "getAll: builtIn=${builtInSources.size}, js=${jsExtensions.size}")
        return builtInSources + jsExtensions
    }

    fun get(sourceId: Long): Source? =
        getAll().find { it.id == sourceId }

    fun getExtensionsPath(): String = extensionLoader.getExtensionsPath()
}