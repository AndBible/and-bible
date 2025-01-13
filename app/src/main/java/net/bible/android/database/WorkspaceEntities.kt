/*
 * Copyright (c) 2019-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
    ) {
        fun deepCopy(): PageManager = PageManager(
            windowId = windowId,
            biblePage = biblePage.copy(),
            commentaryPage = commentaryPage?.copy(),
            dictionaryPage = dictionaryPage?.copy(),
            generalBookPage = generalBookPage?.copy(),
            mapPage = mapPage?.copy(),
            currentCategoryName = currentCategoryName,
            textDisplaySettings = textDisplaySettings?.copy()
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
    )

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
    ) {
        // This is saved to database in WorkspaceSettings. Here just to get it through to activities in SettingsBundle
        @Ignore var workspaceColor: Int? = null
        fun toJson(): String {
            return json.encodeToString(serializer(), this)
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
        }

        fun getValue(type: Types): Any? = when(type) {
            Types.STRONGS -> strongsMode
            Types.MORPH -> showMorphology
            Types.FOOTNOTES -> showFootNotes
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
        }

        fun setValue(type: Types, value: Any?) {
            when(type) {
                Types.STRONGS -> strongsMode = value as Int?
                Types.MORPH -> showMorphology = value as Boolean?
                Types.FOOTNOTES -> showFootNotes = value as Boolean?
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
                showPageNumber = false
            )

            fun actual(pageManagerEntity: PageManager?, workspaceEntity: Workspace?): TextDisplaySettings {
                val pg = pageManagerEntity?.textDisplaySettings
                val ws = workspaceEntity?.textDisplaySettings
                val def = default
                return actual(pg?: ws?: def, ws?: def)
            }

            fun actual(pageManagerSettings: TextDisplaySettings?, workspaceSettings: TextDisplaySettings): TextDisplaySettings {
                val pg = pageManagerSettings
                val ws = workspaceSettings
                val def = default
                val result = TextDisplaySettings()
                for(t in Types.values()) {
                    result.setValue(t, pg?.getValue(t) ?: ws.getValue(t)?: def.getValue(t)!!)
                }
                return result
            }

            fun markNonSpecific(pageManagerSettings: TextDisplaySettings?, workspaceSettings: TextDisplaySettings) {
                val pg = pageManagerSettings
                val ws = workspaceSettings

                if(pg == null) return
                for(t in Types.values()) {
                    if(pg.getValue(t) == ws.getValue(t)) {
                        pg.setNonSpecific(t)
                    }
                }
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
        @ColumnInfo(defaultValue = "NULL") var hideCompareDocuments: MutableSet<String> = mutableSetOf(),
        @ColumnInfo(defaultValue = "0") var limitAmbiguousModalSize: Boolean = false,
        @ColumnInfo(defaultValue = "NULL") var workspaceColor: Int? = defaultWorkspaceColor,
    ) {
        companion object {
            val default get() = WorkspaceSettings()
        }
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
            workspaceSettings = workspaceSettings?.copy(),
            unPinnedWeight = unPinnedWeight,
            maximizedWindowId = maximizedWindowId,
            primaryTargetLinksWindowId = primaryTargetLinksWindowId
        )
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

@Serializable
data class SettingsBundle (
    val workspaceId: IdType,
    val workspaceName: String,
    val workspaceSettings: WorkspaceEntities.TextDisplaySettings,
    val pageManagerSettings: WorkspaceEntities.TextDisplaySettings? = null,
    val windowId: IdType? = null,
) {
    val actualSettings: WorkspaceEntities.TextDisplaySettings get() =
        if(windowId == null)
            WorkspaceEntities.TextDisplaySettings.actual(null, workspaceSettings)
        else
            WorkspaceEntities.TextDisplaySettings.actual(pageManagerSettings!!, workspaceSettings)

    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
    companion object {
        fun fromJson(jsonString: String): SettingsBundle {
            return json.decodeFromString(serializer(), jsonString)
        }
    }

}

