package com.example.y2akio_mobile.util

import org.json.JSONArray
import org.json.JSONObject

/*
 * Small org.json helpers mirroring the TryGetProperty / ValueKind checks of the desktop app:
 * every accessor is null-safe and type-checked, so scrapers can walk unknown payloads freely.
 */

/** String property, "" when missing or not a string. */
fun JSONObject?.str(name: String): String {
    val v = this?.opt(name)
    return if (v is String) v else ""
}

/** Int property that may arrive as a number or a numeric string. */
fun JSONObject?.int(name: String): Int = when (val v = this?.opt(name)) {
    is Number -> v.toInt()
    is String -> v.toIntOrNull() ?: 0
    else -> 0
}

fun JSONObject?.num(name: String): Double? = (this?.opt(name) as? Number)?.toDouble()

fun JSONObject?.obj(name: String): JSONObject? = this?.opt(name) as? JSONObject

fun JSONObject?.arr(name: String): JSONArray? = this?.opt(name) as? JSONArray

fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) (opt(i) as? JSONObject)?.let(out::add)
    return out
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) (opt(i) as? String)?.takeIf { it.isNotBlank() }?.let(out::add)
    return out
}

fun JSONObject.keyList(): List<String> = keys().asSequence().toList()

/** Parses a JSON object, or null when the text isn't one. */
fun parseJsonObject(text: String): JSONObject? = try {
    JSONObject(text)
} catch (_: Exception) {
    null
}

/** Depth-first walk over every JSONObject in a payload (bounded depth, like the desktop scrapers). */
fun walkObjects(root: Any?, maxDepth: Int, visit: (JSONObject) -> Boolean) {
    fun go(el: Any?, depth: Int): Boolean {
        if (depth > maxDepth) return false
        when (el) {
            is JSONObject -> {
                if (visit(el)) return true
                for (key in el.keyList()) if (go(el.opt(key), depth + 1)) return true
            }
            is JSONArray -> for (i in 0 until el.length()) if (go(el.opt(i), depth + 1)) return true
        }
        return false
    }
    go(root, 0)
}

/** First object (depth-first) for which [predicate] is true. */
fun findObject(root: Any?, maxDepth: Int, predicate: (JSONObject) -> Boolean): JSONObject? {
    var found: JSONObject? = null
    walkObjects(root, maxDepth) { if (predicate(it)) { found = it; true } else false }
    return found
}

/** First value of property [name] that is an object, searched depth-first. */
fun findProperty(root: Any?, name: String, maxDepth: Int): JSONObject? =
    findObject(root, maxDepth) { it.opt(name) is JSONObject }?.obj(name)

/** Every object for which [predicate] is true (keeps walking inside matches). */
fun collectObjects(root: Any?, maxDepth: Int, predicate: (JSONObject) -> Boolean): List<JSONObject> {
    val out = ArrayList<JSONObject>()
    walkObjects(root, maxDepth) { if (predicate(it)) out.add(it); false }
    return out
}

fun firstLine(text: String): String {
    val line = text.split('\n').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
    return if (line.length > 120) line.substring(0, 120) + "…" else line
}
