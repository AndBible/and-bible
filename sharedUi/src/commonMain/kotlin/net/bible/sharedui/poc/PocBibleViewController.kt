package net.bible.sharedui.poc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.bible.sharedui.webview.BridgeHandler
import net.bible.sharedui.webview.emitJs

/** Ordinal of Ephesians 2:1 in the PoC fixture (app/bibleview-js/ios/poc-document.json). */
const val POC_FIRST_VERSE_ORDINAL = 36294

/**
 * One PoC BibleView pane's bridge side: replays Android's start-up sequence
 * (BibleView.kt: `set_config` with `initial`, then `clear_document` + `add_documents`) when the page
 * reports ready, and turns `scrolledToOrdinal` into the verse shown in the toolbar title.
 */
class PocBibleViewController(
    val windowId: String,
    private val documentJson: String,
    private val darkTheme: Boolean,
) {
    private val verse = MutableStateFlow<Int?>(null)
    val currentVerse: StateFlow<Int?> = verse

    fun titleFor(verse: Int?): String = if (verse == null) "Ephesians 2" else "Ephesians 2:$verse"

    private fun configJson(): String = buildJsonObject {
        putJsonObject("config") {}
        putJsonObject("appSettings") {
            put("activeWindow", true)
            put("nightMode", darkTheme)
            put("windowId", windowId)
        }
        put("initial", true)
    }.toString()

    val handlers: Map<String, BridgeHandler> = mapOf(
        "setClientReady" to BridgeHandler { _, js ->
            js.evaluate(emitJs("set_config", configJson()))
            js.evaluate(emitJs("clear_document"))
            js.evaluate(emitJs("add_documents", documentJson))
        },
        "scrolledToOrdinal" to BridgeHandler { args, _ ->
            val ordinal = (args.getOrNull(1) as? JsonPrimitive)?.content?.toIntOrNull()
            if (ordinal != null && ordinal >= POC_FIRST_VERSE_ORDINAL) verse.value = ordinal - POC_FIRST_VERSE_ORDINAL + 1
        },
        "console" to BridgeHandler { _, _ -> },
    )
}
