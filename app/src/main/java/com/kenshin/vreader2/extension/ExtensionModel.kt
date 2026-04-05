package com.kenshin.vreader2.extension

data class PluginJson(
    val metadata: PluginMetadata,
    val script: PluginScript,
)

data class PluginMetadata(
    val name: String,
    val author: String = "",
    val version: Int = 1,
    val source: String = "",
    val regexp: String = "",
    val description: String = "",
    val locale: String = "vi_VN",
    val tag: String = "",
    val type: String = "novel",
)

data class PluginScript(
    val home: String? = null,
    val genre: String? = null,
    val detail: String,
    val search: String? = null,
    val page: String? = null,
    val toc: String,
    val chap: String,
)

data class LoadedExtension(
    val metadata: PluginMetadata,
    val pluginScript: PluginScript,
    val scripts: Map<String, String>,
    val iconPath: String?,
)

