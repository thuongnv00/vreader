package com.kenshin.vreader2.extension

import android.content.Context
import com.google.gson.Gson
import com.kenshin.vreader2.extension.js.JsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    // Thư mục chứa các extension trên điện thoại
    // /Android/data/com.kenshin.vreader2/files/extensions/
    private val extensionsDir: File
        get() = File(context.getExternalFilesDir(null), "extensions")

    /**
     * Quét và load tất cả extension đã cài
     */
    fun loadAll(): List<LoadedExtension> {
        if (!extensionsDir.exists()) {
            extensionsDir.mkdirs()
            android.util.Log.d("ExtensionLoader", "Extensions dir not found, created")
            return emptyList()
        }

        val folders = extensionsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        android.util.Log.d("ExtensionLoader", "Found ${folders.size} extension folders: ${folders.map { it.name }}")

        return folders.mapNotNull { folder ->
            android.util.Log.d("ExtensionLoader", "Loading extension: ${folder.name}")
            val ext = loadExtension(folder)
            android.util.Log.d("ExtensionLoader", "Loaded: ${ext?.metadata?.name}, scripts: ${ext?.scripts?.keys}")
            ext
        }
    }

    /**
     * Load 1 extension từ folder
     */
    fun loadExtension(folder: File): LoadedExtension? {
        return try {
            // Đọc plugin.json
            val pluginFile = File(folder, "plugin.json")
            if (!pluginFile.exists()) return null

            val plugin = gson.fromJson(pluginFile.readText(), PluginJson::class.java)

            // Đọc tất cả script JS trong thư mục src/
            val srcDir   = File(folder, "src")
            val scripts  = mutableMapOf<String, String>()

            if (srcDir.exists()) {
                srcDir.listFiles()
                    ?.filter { it.extension == "js" }
                    ?.forEach { jsFile ->
                        scripts[jsFile.nameWithoutExtension] = jsFile.readText()
                    }
            }

            // Icon
            val iconFile = File(folder, "icon.png")

            LoadedExtension(
                metadata  = plugin.metadata,
                pluginScript = plugin.script,
                scripts   = scripts,
                iconPath  = if (iconFile.exists()) iconFile.absolutePath else null,
            )
        } catch (e: Exception) {
            android.util.Log.e("ExtensionLoader", "Failed to load ${folder.name}", e)
            null
        }
    }

    /**
     * Tạo JsSource từ LoadedExtension để dùng như 1 Source bình thường
     */
    fun createJsSource(ext: LoadedExtension): JsSource {
        return JsSource(
            extension = ext,
            engine    = JsEngine(client),
        )
    }

    /**
     * Lấy đường dẫn thư mục extensions để hiển thị cho user biết
     * nơi cần copy extension vào
     */
    fun getExtensionsPath(): String = extensionsDir.absolutePath
}

