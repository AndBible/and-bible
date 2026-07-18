/*
 * Copyright (c) 2019-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database

import android.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.bible.android.database.bookmarks.SpeakSettings
import org.crosswire.jsword.passage.Verse as JswordVerse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import java.util.*

val json = Json {
    allowStructuredMapKeys = true
    encodeDefaults = true
}
val defaultWorkspaceColor = Color.parseColor("#ff444444")

class WorkspaceEntities {
    data class Page(
        val document: String?,
        val key: String?,
        @ColumnInfo(defaultValue = "NULL") val anchorOrdinal: Int?,
    )

    data class Verse(
        val versification: String,
        val bibleBook: Int,
        val chapterNo: Int,
        val verseNo: Int
    ) {
        val jswordVerse: JswordVerse get() {
            val v11n = Versifications.instance().getVersification(versification)
            val bibleBookNo = bibleBook
            val chapterNo = chapterNo
            val verseNo = verseNo
            return JswordVerse(v11n, BibleBook.values()[bibleBookNo], chapterNo, verseNo, true)
        }
    }

    data class BiblePage(
        val document: String?,
        @Embedded(prefix="verse_") val verse: Verse
    )

    data class CommentaryPage(
        val document: String?,
        @ColumnInfo(defaultValue = "NULL") val anchorOrdinal: Int?,
        @ColumnInfo(defaultValue = "NULL") val sourceBookAndKey: String?,
    )

    @Entity(
        foreignKeys = [
            ForeignKey(
                entity = Window::class,
                parentColumns = ["id"],
                childColumns = ["windowId"],
                onDelete = CASCADE
            )],
        indices = [
            Index("windowId", unique = true)
        ]
    )
    data class PageManager(
        @PrimaryKey var windowId: IdType,
        @Embedded(prefix="bible_") val biblePage: BiblePage,
        @Embedded(prefix="commentary_") val commentaryPage: CommentaryPage?,
        @Embedded(prefix="dictionary_") val dictionaryPage: Page?,
        @Embedded(prefix="general_book_") val generalBookPage: Page?,
        @Embedded(prefix="map_") val mapPage: Page?,
        val currentCategoryName: String,
        @Embedded(prefix="text_display_settings_") var textDisplaySettings: TextDisplaySettings?,
        var jsState: String?,
    ) {
        fun deepCopy(): PageManager = PageManager(
            windowId = windowId,
            biblePage = biblePage.copy(),
            commentaryPage = commentaryPage?.copy(),
            dictionaryPage = dictionaryPage?.copy(),
            generalBookPage = generalBookPage?.copy(),
            mapPage = mapPage?.copy(),
            currentCategoryName = currentCategoryName,
            textDisplaySettings = textDisplaySettings?.copy(),
            jsState = jsState
        )
    }

    data class WindowLayout(
        val state: String,
        val weight: Float = 1.0f
    )

    @Serializable
    data class MarginSize(
        @ColumnInfo(defaultValue = "NULL") var marginLeft: Int?,
        @ColumnInfo(defaultValue = "NULL") var marginRight: Int?,
        @ColumnInfo(defaultValue = "NULL") var maxWidth: Int?
    ) {
        /**
         * Returns a new MarginSize where each field from [override] takes precedence over this
         * one's field if non-null. Used by [TextDisplaySettings.actual] to fall back per-field
         * across the hierarchy so sub-object null values inherit from parent/default instead
         * of the whole object being taken as-is.
         */
        fun merge(override: MarginSize?): MarginSize {
            if (override == null) return this
            return MarginSize(
                marginLeft = override.marginLeft ?: marginLeft,
                marginRight = override.marginRight ?: marginRight,
                maxWidth = override.maxWidth ?: maxWidth,
            )
        }
    }

    @Serializable
    data class Colors(
        // Workspace colors (dayWorkspaceColor and nightWorkspaceColor) are not really a TextDisplaySetting but a
        // WorkspaceSetting. But for convenience reason they are held here as UI-wise they need to be held in
        // Color settings.
        @ColumnInfo(defaultValue = "NULL") var dayTextColor: Int?,
        @ColumnInfo(defaultValue = "NULL") var dayBackground: Int?,
        @ColumnInfo(defaultValue = "NULL") var dayNoise: Int?,
        @ColumnInfo(defaultValue = "NULL") var nightTextColor: Int?,
        @ColumnInfo(defaultValue = "NULL") var nightBackground: Int?,
        @ColumnInfo(defaultValue = "NULL") var nightNoise: Int?,
        @ColumnInfo(defaultValue = "NULL") var dayBackgroundImage: String?,
        @ColumnInfo(defaultValue = "NULL") var nightBackgroundImage: String?,
        @ColumnInfo(defaultValue = "NULL") var dayBackgroundImageOpacity: Int?,
        @ColumnInfo(defaultValue = "NULL") var nightBackgroundImageOpacity: Int?,
    ) {
        // This is saved to database in WorkspaceSettings. Here just to get it through to activities in SettingsBundle
        @Ignore var workspaceColor: Int? = null
        fun toJson(): String {
            return json.encodeToString(serializer(), this)
        }

        /**
         * Returns a new Colors where each field from [override] takes precedence over this
         * one's field if non-null. Used by [TextDisplaySettings.actual] to fall back per-field
         * across the hierarchy so sub-object null values inherit from parent/default instead
         * of the whole object being taken as-is.
         *
         * Note: the non-constructor [workspaceColor] field (@Ignore) is not copied — this
         * matches the existing behavior of `Colors.copy()` used elsewhere in actual().
         */
        fun merge(override: Colors?): Colors {
            if (override == null) return this
            return Colors(
                dayTextColor = override.dayTextColor ?: dayTextColor,
                dayBackground = override.dayBackground ?: dayBackground,
                dayNoise = override.dayNoise ?: dayNoise,
                nightTextColor = override.nightTextColor ?: nightTextColor,
                nightBackground = override.nightBackground ?: nightBackground,
                nightNoise = override.nightNoise ?: nightNoise,
                dayBackgroundImage = override.dayBackgroundImage ?: dayBackgroundImage,
                nightBackgroundImage = override.nightBackgroundImage ?: nightBackgroundImage,
                dayBackgroundImageOpacity = override.dayBackgroundImageOpacity ?: dayBackgroundImageOpacity,
                nightBackgroundImageOpacity = override.nightBackgroundImageOpacity ?: nightBackgroundImageOpacity,
            )
        }

        companion object {
            fun fromJson(jsonString: String): Colors {
                return json.decodeFromString(serializer(), jsonString)
            }
        }
    }

    @Serializable
    data class TextDisplaySettings(
        @Embedded(prefix="margin_size_") var marginSize: MarginSize? = null,
        @Embedded(prefix="colors_") var colors: Colors? = null,
        @ColumnInfo(defaultValue = "NULL") var strongsMode: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var showMorphology: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showFootNotes: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showFootNotesInline: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var expandXrefs: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showXrefs: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showRedLetters: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showSectionTitles: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showVerseNumbers: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showVersePerLine: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showBookmarks: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showMyNotes: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var justifyText: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var hyphenation: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var topMargin: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var fontSize: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var fontFamily: String? = null,
        @ColumnInfo(defaultValue = "NULL") var lineSpacing: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var bookmarksHideLabels: List<IdType>? = null,
        @ColumnInfo(defaultValue = "NULL") var showPageNumber: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var infiniteScroll: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var nonStrongsWordItalic: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showMarkAsReadButton: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showTitleScrollButton: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showMemorizationIndicators: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var autoTrackReading: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showAiDocMarkers: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var pageScrollAmount: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var scrollHelperLines: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var scrollHelperLineStyle: Int? = null,
        @ColumnInfo(defaultValue = "NULL") var showPageButtons: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showOrdinals: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showReadingProgress: Boolean? = null,
    ) {
        enum class Types {
            FONTSIZE,
            FONTFAMILY,
            COLORS,
            MARGINSIZE,
            JUSTIFY,
            HYPHENATION,
            TOPMARGIN,
            LINE_SPACING,
            STRONGS,
            MORPH,
            FOOTNOTES,
            FOOTNOTES_INLINE,
            EXPAND_XREFS,
            XREFS,
            REDLETTERS,
            SECTIONTITLES,
            VERSENUMBERS,
            VERSEPERLINE,
            BOOKMARKS_SHOW,
            BOOKMARKS_HIDELABELS,
            MYNOTES,
            PAGENUMBER,
            INFINITE_SCROLL,
            NON_STRONGS_WORD_ITALIC,
            MARK_AS_READ_BUTTON,
            TITLE_SCROLL_BUTTON,
            MEMORIZATION_INDICATORS,
            AUTO_TRACK_READING,
            AI_DOC_MARKERS,
            PAGE_SCROLL_AMOUNT,
            SCROLL_HELPER_LINES,
            SCROLL_HELPER_LINE_STYLE,
            PAGE_BUTTONS,
            ORDINALS,
            SHOW_READING_PROGRESS,
        }

        fun getValue(type: Types): Any? = when(type) {
            Types.STRONGS -> strongsMode
            Types.MORPH -> showMorphology
            Types.FOOTNOTES -> showFootNotes
            Types.FOOTNOTES_INLINE -> showFootNotesInline
            Types.EXPAND_XREFS -> expandXrefs
            Types.XREFS -> showXrefs
            Types.REDLETTERS -> showRedLetters
            Types.SECTIONTITLES -> showSectionTitles
            Types.VERSENUMBERS -> showVerseNumbers
            Types.VERSEPERLINE -> showVersePerLine
            Types.MYNOTES -> showMyNotes
            Types.MARGINSIZE -> marginSize?.copy()
            Types.COLORS -> colors?.copy()
            Types.JUSTIFY -> justifyText
            Types.HYPHENATION -> hyphenation
            Types.TOPMARGIN -> topMargin
            Types.LINE_SPACING -> lineSpacing
            Types.FONTSIZE -> fontSize
            Types.FONTFAMILY -> fontFamily
            Types.BOOKMARKS_SHOW -> showBookmarks
            Types.BOOKMARKS_HIDELABELS -> bookmarksHideLabels
            Types.PAGENUMBER -> showPageNumber
            Types.INFINITE_SCROLL -> infiniteScroll
            Types.NON_STRONGS_WORD_ITALIC -> nonStrongsWordItalic
            Types.MARK_AS_READ_BUTTON -> showMarkAsReadButton
            Types.TITLE_SCROLL_BUTTON -> showTitleScrollButton
            Types.MEMORIZATION_INDICATORS -> showMemorizationIndicators
            Types.AUTO_TRACK_READING -> autoTrackReading
            Types.AI_DOC_MARKERS -> showAiDocMarkers
            Types.PAGE_SCROLL_AMOUNT -> pageScrollAmount
            Types.SCROLL_HELPER_LINES -> scrollHelperLines
            Types.SCROLL_HELPER_LINE_STYLE -> scrollHelperLineStyle
            Types.PAGE_BUTTONS -> showPageButtons
            Types.ORDINALS -> showOrdinals
            Types.SHOW_READING_PROGRESS -> showReadingProgress
        }

        fun setValue(type: Types, value: Any?) {
            when(type) {
                Types.STRONGS -> strongsMode = (value as Int?)?.let { if (it > 2) 0 else it }
                Types.MORPH -> showMorphology = value as Boolean?
                Types.FOOTNOTES -> showFootNotes = value as Boolean?
                Types.FOOTNOTES_INLINE -> showFootNotesInline = value as Boolean?
                Types.EXPAND_XREFS -> expandXrefs = value as Boolean?
                Types.XREFS -> showXrefs = value as Boolean?
                Types.REDLETTERS -> showRedLetters = value as Boolean?
                Types.SECTIONTITLES -> showSectionTitles = value as Boolean?
                Types.VERSENUMBERS -> showVerseNumbers = value as Boolean?
                Types.VERSEPERLINE -> showVersePerLine = value as Boolean?
                Types.MYNOTES -> showMyNotes = value as Boolean?
                Types.MARGINSIZE -> marginSize = value as MarginSize?
                Types.COLORS -> colors = value as Colors?
                Types.JUSTIFY -> justifyText = value as Boolean?
                Types.HYPHENATION -> hyphenation = value as Boolean?
                Types.TOPMARGIN -> topMargin = value as Int?
                Types.FONTSIZE -> fontSize = value as Int?
                Types.FONTFAMILY -> fontFamily = value as String?
                Types.LINE_SPACING -> lineSpacing = value as Int?
                Types.BOOKMARKS_SHOW -> showBookmarks = value as Boolean?
                Types.BOOKMARKS_HIDELABELS -> bookmarksHideLabels = value as List<IdType>?
                Types.PAGENUMBER -> showPageNumber = value as Boolean?
                Types.INFINITE_SCROLL -> infiniteScroll = value as Boolean?
                Types.NON_STRONGS_WORD_ITALIC -> nonStrongsWordItalic = value as Boolean?
                Types.MARK_AS_READ_BUTTON -> showMarkAsReadButton = value as Boolean?
                Types.TITLE_SCROLL_BUTTON -> showTitleScrollButton = value as Boolean?
                Types.MEMORIZATION_INDICATORS -> showMemorizationIndicators = value as Boolean?
                Types.AUTO_TRACK_READING -> autoTrackReading = value as Boolean?
                Types.AI_DOC_MARKERS -> showAiDocMarkers = value as Boolean?
                Types.PAGE_SCROLL_AMOUNT -> pageScrollAmount = value as Int?
                Types.SCROLL_HELPER_LINES -> scrollHelperLines = value as Boolean?
                Types.SCROLL_HELPER_LINE_STYLE -> scrollHelperLineStyle = value as Int?
                Types.PAGE_BUTTONS -> showPageButtons = value as Boolean?
                Types.ORDINALS -> showOrdinals = value as Boolean?
                Types.SHOW_READING_PROGRESS -> showReadingProgress = value as Boolean?
            }
        }

        fun setNonSpecific(type: Types) {
            setValue(type, null)
        }

        fun toJson(): String {
            return json.encodeToString(serializer(), this)
        }

        fun copyFrom(textDisplaySettings: TextDisplaySettings) {
            for(t in Types.values()) {
                setValue(t, textDisplaySettings.getValue(t))
            }
        }

        companion object {
            fun fromJson(jsonString: String): TextDisplaySettings {
                return json.decodeFromString(serializer(), jsonString)
            }
            const val white = -1
            const val black = -16777216

            val default get() = TextDisplaySettings(
                colors = Colors(
                    dayBackground = white,
                    dayTextColor = black,
                    nightBackground = black,
                    nightTextColor = white,
                    nightNoise = 0,
                    dayNoise = 0,
                    dayBackgroundImage = null,
                    nightBackgroundImage = null,
                    dayBackgroundImageOpacity = 100,
                    nightBackgroundImageOpacity = 100,
                ),
                marginSize = MarginSize(
                    marginLeft = 3,
                    marginRight = 3,
                    maxWidth = 170
                ),
                fontSize = 16,
                fontFamily = "sans-serif",
                strongsMode = 0,
                showMorphology = false,
                expandXrefs = false,
                showFootNotes = true,
                showFootNotesInline = false,
                showXrefs = true,
                showRedLetters = true,
                showSectionTitles = true,
                showVerseNumbers = true,
                showVersePerLine = false,
                showMyNotes = true,
                justifyText = true,
                hyphenation = true,
                topMargin = 0,
                lineSpacing = 16,
                showBookmarks = true,
                bookmarksHideLabels = emptyList(),
                showPageNumber = false,
                infiniteScroll = true,
                nonStrongsWordItalic = false,
                showMarkAsReadButton = true,
                showTitleScrollButton = false,
                showMemorizationIndicators = true,
                autoTrackReading = false,
                showAiDocMarkers = true,
                pageScrollAmount = 100,
                scrollHelperLines = false,
                scrollHelperLineStyle = 0,
                showPageButtons = false,
                showOrdinals = false,
                showReadingProgress = false,
            )

            fun actual(
                pageManagerSettings: TextDisplaySettings?,
                workspaceSettings: TextDisplaySettings,
                globalSettings: TextDisplaySettings = TextDisplaySettings()
            ): TextDisplaySettings {
                val def = default
                val result = TextDisplaySettings()
                for(t in Types.values()) {
                    when (t) {
                        // Sub-object types resolve field-by-field: stored values may have some
                        // fields null (meaning "inherit from parent"), so taking the whole object
                        // from the most specific non-null level would leave those fields null in
                        // the actualSettings sent to BibleView. Field-level merge guarantees every
                        // field ends up non-null by falling back through the hierarchy to default.
                        Types.MARGINSIZE -> {
                            val merged = def.marginSize!!
                                .merge(globalSettings.marginSize)
                                .merge(workspaceSettings.marginSize)
                                .merge(pageManagerSettings?.marginSize)
                            result.setValue(t, merged)
                        }
                        Types.COLORS -> {
                            val merged = def.colors!!
                                .merge(globalSettings.colors)
                                .merge(workspaceSettings.colors)
                                .merge(pageManagerSettings?.colors)
                            result.setValue(t, merged)
                        }
                        else -> {
                            result.setValue(t,
                                pageManagerSettings?.getValue(t)
                                    ?: workspaceSettings.getValue(t)
                                    ?: globalSettings.getValue(t)
                                    ?: def.getValue(t)!!
                            )
                        }
                    }
                }
                return result
            }

            fun markNonSpecific(
                pageManagerSettings: TextDisplaySettings?,
                workspaceSettings: TextDisplaySettings,
                globalSettings: TextDisplaySettings = TextDisplaySettings()
            ) {
                val pg = pageManagerSettings

                if(pg == null) return
                val def = default
                for(t in Types.values()) {
                    val parentValue = workspaceSettings.getValue(t)
                        ?: globalSettings.getValue(t)
                        ?: def.getValue(t)
                    if(pg.getValue(t) == parentValue) {
                        pg.setNonSpecific(t)
                    }
                }
            }

            /**
             * Propagate a global settings change to workspaces and their windows.
             * Nulls values that match their effective parent so they inherit instead.
             *
             * @param dirtyTypes which setting types changed
             * @param globalSettings the new global settings
             * @param workspacesWithWindows list of (workspaceTds, list of windowTds) pairs
             * @return true if any settings were modified
             */
            fun propagateGlobalChange(
                dirtyTypes: Set<Types>,
                globalSettings: TextDisplaySettings,
                workspacesWithWindows: List<Pair<TextDisplaySettings, List<TextDisplaySettings>>>
            ): Boolean {
                var anyChanged = false
                for ((wsTds, windowTdsList) in workspacesWithWindows) {
                    for (t in dirtyTypes) {
                        if (wsTds.getValue(t) == globalSettings.getValue(t)) {
                            wsTds.setNonSpecific(t)
                            anyChanged = true
                        }
                    }
                    for (winTds in windowTdsList) {
                        for (t in dirtyTypes) {
                            val parentValue = wsTds.getValue(t)
                                ?: globalSettings.getValue(t)
                                ?: default.getValue(t)
                            if (winTds.getValue(t) == parentValue) {
                                winTds.setNonSpecific(t)
                                anyChanged = true
                            }
                        }
                    }
                }
                return anyChanged
            }
        }
    }

    @Serializable
    data class RecentLabel(val labelId: IdType, var lastAccess: Long)

    @Serializable
    data class WorkspaceSettings(
        @ColumnInfo(defaultValue = "0") var enableTiltToScroll: Boolean = false,
        @ColumnInfo(defaultValue = "0") var enableReverseSplitMode: Boolean = false,
        @ColumnInfo(defaultValue = "1") var autoPin: Boolean = false,
        @ColumnInfo(defaultValue = "NULL") var speakSettings: SpeakSettings? = null,

        @ColumnInfo(defaultValue = "NULL") var recentLabels: MutableList<RecentLabel> = mutableListOf(),
        @ColumnInfo(defaultValue = "NULL") var autoAssignLabels: MutableSet<IdType> = mutableSetOf(),
        @ColumnInfo(defaultValue = "NULL") var autoAssignPrimaryLabel: IdType? = null,
        @ColumnInfo(defaultValue = "NULL") var studyPadCursors: MutableMap<IdType, Int> = mutableMapOf(),
        @ColumnInfo(defaultValue = "NULL") var hideCompareDocuments: MutableSet<String> = mutableSetOf(),
        @ColumnInfo(defaultValue = "0") var limitAmbiguousModalSize: Boolean = false,
        @ColumnInfo(defaultValue = "NULL") var workspaceColor: Int? = defaultWorkspaceColor,
        @ColumnInfo(defaultValue = "1") var restoreButtonsVisible: Boolean = true,
    ) {
        companion object {
            val default get() = WorkspaceSettings()
        }

        fun deepCopy(): WorkspaceSettings = WorkspaceSettings(
            enableTiltToScroll = enableTiltToScroll,
            enableReverseSplitMode = enableReverseSplitMode,
            autoPin = autoPin,
            speakSettings = speakSettings?.copy(),
            recentLabels = recentLabels.map { it.copy() }.toMutableList(),
            autoAssignLabels = autoAssignLabels.toMutableSet(),
            autoAssignPrimaryLabel = autoAssignPrimaryLabel,
            studyPadCursors = studyPadCursors.toMutableMap(),
            hideCompareDocuments = hideCompareDocuments.toMutableSet(),
            limitAmbiguousModalSize = limitAmbiguousModalSize,
            workspaceColor = workspaceColor,
            restoreButtonsVisible = restoreButtonsVisible
        )
    }

    @Entity
    data class Workspace(
        var name: String,
        var contentsText: String? = null,

        @PrimaryKey var id: IdType = IdType(),
        @ColumnInfo(defaultValue = "0") var orderNumber: Int = 0,

        @Embedded(prefix="text_display_settings_")
        var textDisplaySettings: TextDisplaySettings? = TextDisplaySettings(),

        @Embedded(prefix="workspace_settings_")
        val workspaceSettings: WorkspaceSettings? = WorkspaceSettings(),

        @ColumnInfo(defaultValue = "NULL") var unPinnedWeight: Float? = null,
        val maximizedWindowId: IdType? = null,

        @ColumnInfo(defaultValue = "NULL") var primaryTargetLinksWindowId: IdType? = null,
    ) {
        fun deepCopy(): Workspace = Workspace(
            name = name,
            contentsText = contentsText,
            id = id,
            orderNumber = orderNumber,
            textDisplaySettings = textDisplaySettings?.copy(),
            workspaceSettings = workspaceSettings?.deepCopy(),
            unPinnedWeight = unPinnedWeight,
            maximizedWindowId = maximizedWindowId,
            primaryTargetLinksWindowId = primaryTargetLinksWindowId
        )
    }

    @Entity(
        primaryKeys = ["workspaceId", "labelId"],
        foreignKeys = [
            ForeignKey(
                entity = Workspace::class,
                parentColumns = ["id"],
                childColumns = ["workspaceId"],
                onDelete = CASCADE
            )
        ],
        indices = [Index("workspaceId")]
    )
    @Serializable
    data class WorkspaceLabelOverride(
        val workspaceId: IdType,
        val labelId: IdType,
        @ColumnInfo(defaultValue = "NULL") val overrideMode: Int? = null,
    ) {
        val hasOverride: Boolean get() = overrideMode != null

        companion object {
            const val MODE_HIGHLIGHT = 0
            const val MODE_UNDERLINE = 1
            const val MODE_MARKER = 2
            const val MODE_HIDDEN = 3
        }
    }

    @Entity(
        foreignKeys = [
            ForeignKey(
                entity = Window::class,
                parentColumns = ["id"],
                childColumns = ["windowId"],
                onDelete = CASCADE
            )],
        indices = [
            Index("windowId")
        ]
    )
    data class HistoryItem(
        val windowId: IdType,
        val createdAt: Date,
        val document: String,
        val key: String,
        @ColumnInfo(defaultValue = "NULL")
        val anchorOrdinal: Int?,
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
    )

    @Entity(
        foreignKeys = [
            ForeignKey(
                entity = Workspace::class,
                parentColumns = ["id"],
                childColumns = ["workspaceId"],
                onDelete = CASCADE
            )],
        indices = [
            Index("workspaceId")
        ]
    )
    data class Window(
        var workspaceId: IdType,
        val isSynchronized: Boolean,
        val isPinMode: Boolean,

        val isLinksWindow: Boolean = false,

        @Embedded(prefix="window_layout_") val windowLayout: WindowLayout,

        @PrimaryKey var id: IdType = IdType(),

        var orderNumber: Int = 0,
        @ColumnInfo(defaultValue = "NULL") var targetLinksWindowId: IdType? = null,
        @ColumnInfo(defaultValue = "0") val syncGroup: Int = 0,
    ) {
        fun deepCopy(): Window =
            Window(
                workspaceId = workspaceId,
                isSynchronized = isSynchronized,
                isPinMode = isPinMode,
                isLinksWindow = isLinksWindow,
                windowLayout = windowLayout.copy(),
                id = id,
                orderNumber = orderNumber,
                targetLinksWindowId = targetLinksWindowId,
                syncGroup = syncGroup
        )
    }
}

@Entity
data class GlobalTextDisplaySettings(
    @PrimaryKey val id: IdType = SINGLETON_ID,
    @Embedded(prefix = "text_display_settings_")
    var textDisplaySettings: WorkspaceEntities.TextDisplaySettings = WorkspaceEntities.TextDisplaySettings(),
) {
    companion object {
        /** Fixed ID shared across all devices so sync recognizes it as the same row. */
        val SINGLETON_ID = IdType.fromString("00000000-0000-0000-0000-000000000001")
    }
}

@Serializable
enum class SettingsLevel { GLOBAL, WORKSPACE, WINDOW }

enum class InheritedFrom { NONE, WORKSPACE, GLOBAL }

@Serializable
data class SettingsBundle (
    val level: SettingsLevel = SettingsLevel.WORKSPACE,
    val workspaceId: IdType = IdType.empty(),
    val workspaceName: String = "",
    val globalSettings: WorkspaceEntities.TextDisplaySettings = WorkspaceEntities.TextDisplaySettings(),
    val workspaceSettings: WorkspaceEntities.TextDisplaySettings = WorkspaceEntities.TextDisplaySettings(),
    val pageManagerSettings: WorkspaceEntities.TextDisplaySettings? = null,
    val windowId: IdType? = null,
) {
    val actualSettings: WorkspaceEntities.TextDisplaySettings get() =
        WorkspaceEntities.TextDisplaySettings.actual(pageManagerSettings, workspaceSettings, globalSettings)

    /**
     * Where the effective value for [type] originates relative to this bundle's [level].
     * - [InheritedFrom.NONE]: the value is set at this level (the user owns it here).
     * - [InheritedFrom.WORKSPACE]: at WINDOW level, the value is null at window but set at workspace.
     * - [InheritedFrom.GLOBAL]: the value falls through to global/defaults.
     */
    fun inheritedFrom(type: WorkspaceEntities.TextDisplaySettings.Types): InheritedFrom = when (level) {
        SettingsLevel.WINDOW -> when {
            pageManagerSettings?.getValue(type) != null -> InheritedFrom.NONE
            workspaceSettings.getValue(type) != null -> InheritedFrom.WORKSPACE
            else -> InheritedFrom.GLOBAL
        }
        SettingsLevel.WORKSPACE -> when {
            workspaceSettings.getValue(type) != null -> InheritedFrom.NONE
            else -> InheritedFrom.GLOBAL
        }
        SettingsLevel.GLOBAL -> InheritedFrom.NONE
    }

    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
    companion object {
        fun fromJson(jsonString: String): SettingsBundle {
            return json.decodeFromString(serializer(), jsonString)
        }
    }

}

