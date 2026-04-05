package com.kenshin.vreader2

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class VReaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        copyBuiltInExtensions()
    }

    /**
     * Copy các extension có sẵn từ assets ra bộ nhớ ngoài
     * để ExtensionLoader có thể load
     */
    private fun copyBuiltInExtensions() {
        try {
            val extensionsDir = File(getExternalFilesDir(null), "extensions")
            android.util.Log.d("VReaderApp", "Extensions dir: ${extensionsDir.absolutePath}")
            if (!extensionsDir.exists()) extensionsDir.mkdirs()

            val extFolders = assets.list("extensions") ?: return
            android.util.Log.d("VReaderApp", "Found ${extFolders.size} extensions: ${extFolders.toList()}")

            extFolders.forEach { extName ->
                val destDir = File(extensionsDir, extName)
                android.util.Log.d("VReaderApp", "Processing $extName")
                if (!destDir.exists()) {
                    destDir.mkdirs()
                    copyAssetFolder("extensions/$extName", destDir)
                    android.util.Log.d("VReaderApp", "Copied $extName successfully")
                } else {
                    android.util.Log.d("VReaderApp", "$extName already exists")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VReaderApp", "Failed to copy extensions", e)
        }
    }

    private fun copyAssetFolder(assetPath: String, destDir: File) {
        val files = assets.list(assetPath) ?: return
        files.forEach { fileName ->
            val subAssetPath = "$assetPath/$fileName"
            val destFile     = File(destDir, fileName)

            // Kiểm tra là folder hay file
            val subFiles = assets.list(subAssetPath)
            if (!subFiles.isNullOrEmpty()) {
                // Là folder
                destFile.mkdirs()
                copyAssetFolder(subAssetPath, destFile)
            } else {
                // Là file
                assets.open(subAssetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
