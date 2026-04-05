package com.kenshin.vreader2.extension.js

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable

/**
 * Expose các API cho JavaScript sử dụng:
 * - fetch(url) / fetch(url, options)
 * - Html.parse(text)
 * - Response.success(data)
 * - Response.error(message)
 * - Console.log(...)
 */
class JsBridge(private val client: OkHttpClient) {

    // ── fetch ─────────────────────────────────────────────────────────────────

    fun fetch(url: String): JsResponse {
        return fetchWithOptions(url, null)
    }

    fun fetch(url: String, options: NativeObject?): JsResponse {
        return fetchWithOptions(url, options)
    }

    private fun fetchWithOptions(url: String, options: NativeObject?): JsResponse {
        return try {
            val method = options?.get("method")?.toString() ?: "GET"
            val headers = options?.get("headers") as? NativeObject
            val body    = options?.get("body") as? NativeObject

            val requestBuilder = Request.Builder().url(url)

            // Thêm headers
            headers?.forEach { key, value ->
                requestBuilder.header(key.toString(), value.toString())
            }

            // Thêm body nếu có
            val requestBody = if (body != null && method != "GET") {
                val bodyMap = body.entries.joinToString("&") { (k, v) ->
                    "${k}=${v}"
                }
                bodyMap.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            } else null

            requestBuilder.method(method, requestBody)

            val response = client.newCall(requestBuilder.build()).execute()
            JsResponse(
                status  = response.code,
                ok      = response.isSuccessful,
                headers = response.headers.toMap(),
                body    = response.body?.bytes() ?: byteArrayOf(),
            )
        } catch (e: Exception) {
            JsResponse(status = 0, ok = false, headers = emptyMap(), body = byteArrayOf())
        }
    }

    // ── Html ──────────────────────────────────────────────────────────────────

    fun htmlParse(text: String): Document = Jsoup.parse(text)

    fun htmlParse(text: String, charset: String): Document =
        Jsoup.parse(text.toByteArray(charset(charset)).toString(Charsets.UTF_8))

    fun htmlClean(text: String, allowedTags: NativeArray): String {
        val tags = (0 until allowedTags.length).map { allowedTags[it].toString() }
        val whitelist = org.jsoup.safety.Safelist.none()
        tags.forEach { whitelist.addTags(it) }
        return Jsoup.clean(text, whitelist)
    }
}

// ── JsResponse ────────────────────────────────────────────────────────────────

class JsResponse(
    val status: Int,
    val ok: Boolean,
    val headers: Map<String, String>,
    private val body: ByteArray,
) {
    fun text(): String = body.toString(Charsets.UTF_8)

    fun text(charset: String): String = body.toString(charset(charset))

    fun html(): Document = Jsoup.parse(text())

    fun html(charset: String): Document = Jsoup.parse(text(charset))

    fun json(): Any? {
        val cx = Context.enter()
        return try {
            val scope = cx.initStandardObjects()
            cx.evaluateString(scope, "var __json = ${text()}", "json", 1, null)
            scope.get("__json", scope)
        } finally {
            Context.exit()
        }
    }
}