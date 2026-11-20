/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui.platform.widget

/** What a home-screen widget tap asks the app to do, parsed from an `andbible-widget://` URL. */
sealed interface WidgetUrlAction {
    /** Open a passage, as the equivalent `https://read.andbible.org/<key>?document=..&v11n=..` link would. */
    data class OpenPassage(val key: String, val document: String?, val v11n: String?, val ordinal: Int?) : WidgetUrlAction

    /** A speak transport button. */
    data class Speak(val command: SpeakCommand) : WidgetUrlAction

    /** Resume speaking from the speak bookmark with this id. */
    data class SpeakBookmark(val id: String) : WidgetUrlAction

    /**
     * Speak this passage from its first verse, as the verse menu's Speak does (`andbible-widget://speak-passage?link=<url>`,
     * the Verse widget's speak button). [passage] is parsed from the same `read.andbible.org` link as [OpenPassage].
     */
    data class SpeakPassage(val passage: OpenPassage) : WidgetUrlAction

    /** Malformed or unknown: ignored by the app. */
    data object Invalid : WidgetUrlAction
}

/** The transport buttons a widget can deep-link to (`andbible-widget://speak/<wire name>`). */
enum class SpeakCommand(val wire: String) {
    TOGGLE("toggle"), PLAY("play"), PAUSE("pause"), STOP("stop"),
    PREV_VERSE("prev"), NEXT_VERSE("next"), REWIND("rewind"), FORWARD("forward"),
    /** Toggles the sleep timer (off, or back to the last used length). */
    SLEEP("sleep"),
}

/**
 * Parser of the widget URL scheme (`andbible-widget://open?link=<url>`, `.../speak/<command>`,
 * `.../bookmark/<id>`, `speak-passage?link=<url>`). The inner `link` of an `open` URL must be fully percent-encoded by the sender (`&`, `?`, `=`
 * and `#` included): Swift's `URLComponents`/`URLQueryItem` leave those unescaped, so the widget extension encodes it
 * itself, and an unencoded `&` ends the link. Pure and platform-independent; the iOS app executes the result
 * (`IosWidgetUrls`).
 */
object WidgetUrl {
    const val SCHEME = "andbible-widget"
    private const val READ_PREFIX = "https://read.andbible.org/"

    fun parse(url: String): WidgetUrlAction {
        val prefix = "$SCHEME://"
        if (!url.startsWith(prefix, ignoreCase = true)) return WidgetUrlAction.Invalid
        val rest = url.substring(prefix.length)
        val (path, query) = rest.splitOnce('?')
        val segments = path.trim('/').split('/').filter { it.isNotEmpty() }.map(::decode)
        return when (segments.firstOrNull()) {
            "open" -> query?.let(::queryParams)?.get("link")?.let(::parseReadLink) ?: WidgetUrlAction.Invalid
            "speak" -> SpeakCommand.entries.firstOrNull { it.wire == segments.getOrNull(1) }
                ?.let { WidgetUrlAction.Speak(it) } ?: WidgetUrlAction.Invalid
            "speak-passage" -> (query?.let(::queryParams)?.get("link")?.let(::parseReadLink) as? WidgetUrlAction.OpenPassage)
                ?.let { WidgetUrlAction.SpeakPassage(it) } ?: WidgetUrlAction.Invalid
            "bookmark" -> segments.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let { WidgetUrlAction.SpeakBookmark(it) } ?: WidgetUrlAction.Invalid
            else -> WidgetUrlAction.Invalid
        }
    }

    /** The passage a `https://read.andbible.org/<key>?document=..&v11n=..&ordinal=..` link names. */
    fun parseReadLink(link: String): WidgetUrlAction {
        if (!link.startsWith(READ_PREFIX)) return WidgetUrlAction.Invalid
        val (path, query) = link.substring(READ_PREFIX.length).splitOnce('?')
        val key = decode(path.substringBefore('#')).trim()
        if (key.isEmpty()) return WidgetUrlAction.Invalid
        val params = query?.let(::queryParams).orEmpty()
        return WidgetUrlAction.OpenPassage(key, params["document"], params["v11n"], params["ordinal"]?.toIntOrNull())
    }

    private fun String.splitOnce(c: Char): Pair<String, String?> {
        val i = indexOf(c)
        return if (i < 0) this to null else substring(0, i) to substring(i + 1)
    }

    private fun queryParams(query: String): Map<String, String> =
        query.split('&').filter { it.isNotEmpty() }.associate {
            val (k, v) = it.splitOnce('=')
            decode(k) to decode(v.orEmpty())
        }

    /** Percent-decoding (UTF-8); `+` is a space in query strings only, which `read.andbible.org` links never rely on. */
    private fun decode(s: String): String {
        if ('%' !in s) return s
        val out = ByteArrayBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val hex = if (c == '%' && i + 2 < s.length) s.substring(i + 1, i + 3).takeIf(::isHexPair)?.toInt(16) else null
            if (hex != null) { out.add(hex.toByte()); i += 3 }
            else { c.toString().encodeToByteArray().forEach(out::add); i++ }
        }
        return out.toByteArray().decodeToString()
    }

    /** Exactly two hex digits: `toIntOrNull(16)` alone would accept a sign (`%-1`, `%+f`). */
    private fun isHexPair(s: String) = s.length == 2 && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private class ByteArrayBuilder {
        private val list = ArrayList<Byte>()
        fun add(b: Byte) { list.add(b) }
        fun toByteArray(): ByteArray = list.toByteArray()
    }
}
