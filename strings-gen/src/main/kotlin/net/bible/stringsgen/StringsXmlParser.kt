package net.bible.stringsgen

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Parses an Android `res/values-* /strings.xml` document, returning the `<string name="k">v</string>`
 * entries as an ordered `key → value` map. `<plurals>` / `<string-array>` / `<!-- comments -->` are
 * ignored. Values are Android-unescaped: a single wrapping `"…"` is stripped, then the backslash
 * escapes `\'` `\"` `\n` `\t` `\\` are decoded (XML entities like `&amp;` are already decoded by the DOM).
 *
 * JVM-only (uses `javax.xml`) — this is a build-time generator, its OUTPUT (not this code) is what
 * crosses into commonMain/iOS in later tasks.
 *
 * `\uXXXX` unicode escapes are also decoded (the app's strings.xml uses them heavily — `…`
 * ellipsis, `→` arrow, `★` star, `●` filled circle — which Android's aapt decodes at
 * compile time; without decoding them here the iOS holder would render the literal text `…`).
 */
fun parseStringsXml(xml: String): Map<String, String> {
    val doc = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = false }
        .newDocumentBuilder()
        .parse(xml.byteInputStream())
    doc.documentElement.normalize()

    val result = LinkedHashMap<String, String>()
    val strings = doc.getElementsByTagName("string")
    for (i in 0 until strings.length) {
        val el = strings.item(i) as? Element ?: continue
        val name = el.getAttribute("name")
        if (name.isEmpty()) continue
        result[name] = unescapeAndroid(el.textContent)
    }
    return result
}

/** Parses <plurals name="k"><item quantity="one">..</item>..</plurals> into name -> (quantity -> template). */
fun parsePluralsXml(xml: String): Map<String, Map<String, String>> {
    val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        .newDocumentBuilder().parse(xml.byteInputStream())
    doc.documentElement.normalize()
    val out = LinkedHashMap<String, Map<String, String>>()
    val plurals = doc.getElementsByTagName("plurals")
    for (i in 0 until plurals.length) {
        val el = plurals.item(i) as? Element ?: continue
        val name = el.getAttribute("name"); if (name.isEmpty()) continue
        val items = el.getElementsByTagName("item")
        val byQty = LinkedHashMap<String, String>()
        for (j in 0 until items.length) {
            val it = items.item(j) as? Element ?: continue
            val q = it.getAttribute("quantity"); if (q.isEmpty()) continue
            byQty[q] = unescapeAndroid(it.textContent)
        }
        out[name] = byQty
    }
    return out
}

/** Parses <string-array name="k"><item>..</item>..</string-array> into name -> ordered items. */
fun parseStringArraysXml(xml: String): Map<String, List<String>> {
    val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        .newDocumentBuilder().parse(xml.byteInputStream())
    doc.documentElement.normalize()
    val out = LinkedHashMap<String, List<String>>()
    val arrays = doc.getElementsByTagName("string-array")
    for (i in 0 until arrays.length) {
        val el = arrays.item(i) as? Element ?: continue
        val name = el.getAttribute("name"); if (name.isEmpty()) continue
        val items = el.getElementsByTagName("item")
        val list = ArrayList<String>()
        for (j in 0 until items.length) {
            (items.item(j) as? Element)?.let { list.add(unescapeAndroid(it.textContent)) }
        }
        out[name] = list
    }
    return out
}

/** Applies Android string-resource unescaping (XML entities are already resolved by the DOM). */
private fun unescapeAndroid(raw: String): String {
    // Strip one wrapping pair of double quotes (Android uses "…" to preserve leading/trailing space).
    var s = raw
    if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
        s = s.substring(1, s.length - 1)
    }

    // Decode backslash escapes in a single left-to-right pass (so \\ is consumed as one literal '\').
    val sb = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            val next = s[i + 1]
            // `\uXXXX` — 4 hex digits → the corresponding code unit (Android/aapt supports this).
            if (next == 'u' && i + 5 < s.length && s.substring(i + 2, i + 6).all { it.isHex() }) {
                sb.append(s.substring(i + 2, i + 6).toInt(16).toChar())
                i += 6
                continue
            }
            when (next) {
                '\'' -> sb.append('\'')
                '"' -> sb.append('"')
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                '\\' -> sb.append('\\')
                else -> { sb.append('\\'); sb.append(next) }
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun Char.isHex(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
