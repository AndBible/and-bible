package net.bible.sharedui.poc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.bibleview.JsExpr
import net.bible.sharedcore.bibleview.UnimplementedBibleBridge

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
    /** Read at `set_config` time; IosPocApp updates it on theme flips so panes are never re-created. */
    var darkTheme: Boolean,
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

    /** This pane's bridge: the PoC answers start-up, scrolling and console; everything else is a logged no-op. */
    fun bridge(js: (JsCall) -> Unit, log: (String) -> Unit): BibleBridge =
        object : UnimplementedBibleBridge(log, { callId -> js(BibleViewEvents.response(callId, null)) }) {
            override fun setClientReady() {
                js(BibleViewEvents.setConfig(JsExpr(configJson())))
                js(BibleViewEvents.clearDocument())
                js(BibleViewEvents.addDocuments(JsExpr(documentJson)))
            }

            override fun scrolledToOrdinal(keyStr: String, ordinal: Int) {
                if (ordinal >= POC_FIRST_VERSE_ORDINAL) verse.value = ordinal - POC_FIRST_VERSE_ORDINAL + 1
            }

            override fun console(loggerName: String, message: String) {
                log("js[$windowId]: $loggerName $message")
            }
        }
}
