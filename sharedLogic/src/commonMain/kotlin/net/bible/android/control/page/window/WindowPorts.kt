package net.bible.android.control.page.window

import net.bible.android.control.page.Document
import net.bible.android.control.page.OrdinalRange
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

/**
 * What the window domain ([Window], [WindowRepository], [WindowSync], [WindowControl]) asks of the view that
 * renders a window. The Android implementation is `BibleView`; the domain never names it.
 */
interface WindowView {
    val verseRangeLoaded: VerseRange?
    var lastUpdated: Long
    val firstDocument: Document?
    var listenEvents: Boolean

    fun highlightOrdinalRange(bookInitials: String, osisRef: String, range: IntRange)
    fun highlightBibleOrdinalRange(range: IntRange)
    fun destroy()
    suspend fun loadDocument(
        document: Document,
        updateLocation: Boolean = false,
        key: Key? = null,
        anchorOrdinal: OrdinalRange? = null,
        htmlId: String? = null,
        windowContentGeneration: Long? = null,
    )
    fun scrollOrJumpToVerse(key: Key, forceNow: Boolean = false)
    fun scrollOrJumpToOrdinal(ordinal: OrdinalRange?, htmlId: String?, bookInitials: String?, osisRef: String?, forceNow: Boolean = false)
    fun hasChapterLoaded(chapter: Int): Boolean
    fun requestFocus(): Boolean
    fun updateTextDisplaySettings(onAttach: Boolean = false)
    /** Returns whether the adjustment reached the page. */
    fun adjustLoadingCount(adj: Int): Boolean
}

/** The display title of one text display setting type, as shown in the settings pickers. */
fun interface SettingLabels {
    fun label(bundle: net.bible.android.database.SettingsBundle, type: TextDisplaySettings.Types): String
}

/** Asks the user which text display settings to copy; an empty list means cancel or nothing checked. */
fun interface WindowSettingsPicker {
    suspend fun chooseSettingsToCopy(window: Window, types: List<TextDisplaySettings.Types>): List<TextDisplaySettings.Types>
}
