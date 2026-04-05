package com.kenshin.vreader2.extension.js

import okhttp3.OkHttpClient
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject

class JsEngine(private val client: OkHttpClient) {

    private val bridge = JsBridge(client)

    /**
     * Chạy 1 script JS và gọi hàm execute() với các args truyền vào.
     * Trả về kết quả của hàm execute().
     */
    fun execute(script: String, vararg args: Any?): Any? {
        val cx = Context.enter()
        return try {
            cx.optimizationLevel = -1 // Bắt buộc trên Android (không có JIT)

            val scope = cx.initStandardObjects()

            // Inject các API
            injectFetch(cx, scope)
            injectHtml(cx, scope)
            injectResponse(cx, scope)
            injectConsole(cx, scope)
            injectLoad(cx, scope)
            injectSleep(cx, scope)

            // Chạy script
            cx.evaluateString(scope, script, "extension", 1, null)

            // Gọi hàm execute()
            val executeFunc = scope.get("execute", scope) as? Function
                ?: return null

            executeFunc.call(cx, scope, scope, args)
        } catch (e: Exception) {
            android.util.Log.e("JsEngine", "Error executing script", e)
            android.util.Log.e("JsEngine", "Script content: ${script.take(200)}")
            android.util.Log.e("JsEngine", "Args: ${args.toList()}")
            null
        }finally {
            Context.exit()
        }
    }

    // ── Inject fetch(url) / fetch(url, options) ───────────────────────────────

    private fun injectFetch(cx: Context, scope: ScriptableObject) {
        val fetchFunc = object : org.mozilla.javascript.BaseFunction() {
            override fun call(
                cx: Context,
                scope: org.mozilla.javascript.Scriptable,
                thisObj: org.mozilla.javascript.Scriptable,
                args: Array<out Any?>
            ): Any {
                val url     = args.getOrNull(0)?.toString() ?: return Context.getUndefinedValue()
                val options = args.getOrNull(1) as? NativeObject
                val response = bridge.fetch(url, options)

                // Wrap JsResponse thành NativeObject để JS có thể gọi .text(), .html()...
                val obj = cx.newObject(scope) as NativeObject
                obj.put("status",  obj, response.status)
                obj.put("ok",      obj, response.ok)

                // .text()
                ScriptableObject.putProperty(obj, "text", object : org.mozilla.javascript.BaseFunction() {
                    override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                        val charset = args.getOrNull(0)?.toString()
                        return if (charset != null) response.text(charset) else response.text()
                    }
                })

                // .html()
                ScriptableObject.putProperty(obj, "html", object : org.mozilla.javascript.BaseFunction() {
                    override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                        val charset = args.getOrNull(0)?.toString()
                        val doc = if (charset != null) response.html(charset) else response.html()
                        return wrapDocument(cx, scope, doc)
                    }
                })

                // .json()
                ScriptableObject.putProperty(obj, "json", object : org.mozilla.javascript.BaseFunction() {
                    override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any? {
                        return response.json()
                    }
                })

                return obj
            }
        }
        ScriptableObject.putProperty(scope, "fetch", fetchFunc)
    }

    // ── Inject Html.parse() / Html.clean() ────────────────────────────────────

    private fun injectHtml(cx: Context, scope: ScriptableObject) {
        val htmlObj = cx.newObject(scope) as NativeObject

        ScriptableObject.putProperty(htmlObj, "parse", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                val text    = args.getOrNull(0)?.toString() ?: return Context.getUndefinedValue()
                val charset = args.getOrNull(1)?.toString()
                val doc     = if (charset != null) bridge.htmlParse(text, charset) else bridge.htmlParse(text)
                return wrapDocument(cx, scope, doc)
            }
        })

        ScriptableObject.putProperty(htmlObj, "clean", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                val text        = args.getOrNull(0)?.toString() ?: return ""
                val allowedTags = args.getOrNull(1) as? NativeArray ?: return text
                return bridge.htmlClean(text, allowedTags)
            }
        })

        ScriptableObject.putProperty(scope, "Html", htmlObj)
    }

    // ── Inject Response.success() / Response.error() ─────────────────────────

    private fun injectResponse(cx: Context, scope: ScriptableObject) {
        val responseObj = cx.newObject(scope) as NativeObject

        ScriptableObject.putProperty(responseObj, "success", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                val obj = cx.newObject(scope) as NativeObject
                obj.put("success", obj, true)
                obj.put("data",    obj, args.getOrNull(0))
                obj.put("data2",   obj, args.getOrNull(1))
                return obj
            }
        })

        ScriptableObject.putProperty(responseObj, "error", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                val obj = cx.newObject(scope) as NativeObject
                obj.put("success", obj, false)
                obj.put("error",   obj, args.getOrNull(0)?.toString() ?: "Unknown error")
                return obj
            }
        })

        ScriptableObject.putProperty(scope, "Response", responseObj)
    }

    // ── Inject Console.log() ──────────────────────────────────────────────────

    private fun injectConsole(cx: Context, scope: ScriptableObject) {
        val consoleObj = cx.newObject(scope) as NativeObject
        ScriptableObject.putProperty(consoleObj, "log", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                android.util.Log.d("JsConsole", args.joinToString(" ") { it?.toString() ?: "null" })
                return Context.getUndefinedValue()
            }
        })
        ScriptableObject.putProperty(scope, "Console", consoleObj)
    }

    // ── Inject load() ─────────────────────────────────────────────────────────

    private fun injectLoad(cx: Context, scope: ScriptableObject) {
        val loadFunc = object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                // Sẽ implement sau khi có ExtensionLoader
                return Context.getUndefinedValue()
            }
        }
        ScriptableObject.putProperty(scope, "load", loadFunc)
    }

    // ── Inject sleep() ────────────────────────────────────────────────────────

    private fun injectSleep(cx: Context, scope: ScriptableObject) {
        val sleepFunc = object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                val ms = args.getOrNull(0)?.toString()?.toLongOrNull() ?: 1000L
                Thread.sleep(ms)
                return Context.getUndefinedValue()
            }
        }
        ScriptableObject.putProperty(scope, "sleep", sleepFunc)
    }

    // ── Wrap JSoup Document thành NativeObject ────────────────────────────────

    private fun wrapDocument(
        cx: Context,
        scope: org.mozilla.javascript.Scriptable,
        doc: org.jsoup.nodes.Document,
    ): NativeObject {
        val obj = cx.newObject(scope) as NativeObject

        // .select(selector) → NativeArray of Element objects
        ScriptableObject.putProperty(obj, "select", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                val selector = args.getOrNull(0)?.toString() ?: return cx.newArray(scope, emptyArray())
                val elements = doc.select(selector)
                val arr = cx.newArray(scope, elements.size)
                elements.forEachIndexed { i, el ->
                    (arr as NativeArray).put(i, arr, wrapElement(cx, scope, el))
                }
                return arr
            }
        })

        // .selectFirst(selector) → Element object or null
        ScriptableObject.putProperty(obj, "selectFirst", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any? {
                val selector = args.getOrNull(0)?.toString() ?: return null
                val el = doc.selectFirst(selector) ?: return null
                return wrapElement(cx, scope, el)
            }
        })

        // .text()
        ScriptableObject.putProperty(obj, "text", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                return doc.text()
            }
        })

        // .html()
        ScriptableObject.putProperty(obj, "html", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                return doc.html()
            }
        })

        return obj
    }

    private fun wrapElement(
        cx: Context,
        scope: org.mozilla.javascript.Scriptable,
        el: org.jsoup.nodes.Element,
    ): NativeObject {
        val obj = cx.newObject(scope) as NativeObject

        ScriptableObject.putProperty(obj, "select", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                val selector = args.getOrNull(0)?.toString() ?: return cx.newArray(scope, emptyArray())
                val elements = el.select(selector)
                val arr = cx.newArray(scope, elements.size)
                elements.forEachIndexed { i, child ->
                    (arr as NativeArray).put(i, arr, wrapElement(cx, scope, child))
                }
                return arr
            }
        })

        ScriptableObject.putProperty(obj, "selectFirst", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any? {
                val selector = args.getOrNull(0)?.toString() ?: return null
                val child = el.selectFirst(selector) ?: return null
                return wrapElement(cx, scope, child)
            }
        })

        ScriptableObject.putProperty(obj, "text", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                return el.text()
            }
        })

        ScriptableObject.putProperty(obj, "html", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                return el.html()
            }
        })

        ScriptableObject.putProperty(obj, "attr", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                val attrName = args.getOrNull(0)?.toString() ?: return ""
                return el.attr(attrName)
            }
        })

        ScriptableObject.putProperty(obj, "outerHtml", object : org.mozilla.javascript.BaseFunction() {
            override fun call(cx: Context, scope: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable, args: Array<out Any?>): Any {
                return el.outerHtml()
            }
        })

        return obj
    }
}