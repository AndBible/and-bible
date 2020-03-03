/*
 * Copyright (c) 2019 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

import java.util.*

val json = Json(JsonConfiguration.Stable)

class WorkspaceEntities {
    data class Page(
        val document: String?,
        val key: String?,
        val currentYOffsetRatio: Float?
    )

    data class Verse(
        val versification: String,
        val bibleBook: Int,
        val chapterNo: Int,
        val verseNo: Int
    )

    data class BiblePage(
        val document: String?,
        @Embedded(prefix="verse_") val verse: Verse
    )

    data class CommentaryPage(
        val document: String?,
        val currentYOffsetRatio: Float?
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
        @PrimaryKey var windowId: Long,
        @Embedded(prefix="bible_") val biblePage: BiblePage,
        @Embedded(prefix="commentary_") val commentaryPage: CommentaryPage?,
        @Embedded(prefix="dictionary_") val dictionaryPage: Page?,
        @Embedded(prefix="general_book_") val generalBookPage: Page?,
        @Embedded(prefix="map_") val mapPage: Page?,
        val currentCategoryName: String,
        @Embedded(prefix="text_display_settings_") val textDisplaySettings: TextDisplaySettings?
    )

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
    data class Font(
        @ColumnInfo(defaultValue = "NULL") var fontSize: Int?,
        @ColumnInfo(defaultValue = "NULL") var fontFamily: String?
    )

    @Serializable
    data class Colors(
        @ColumnInfo(defaultValue = "NULL") var dayTextColor: Int?,
        @ColumnInfo(defaultValue = "NULL") var dayBackground: Int?,
        @ColumnInfo(defaultValue = "NULL") var dayNoise: Int?,
        @ColumnInfo(defaultValue = "NULL") var nightTextColor: Int?,
        @ColumnInfo(defaultValue = "NULL") var nightBackground: Int?,
        @ColumnInfo(defaultValue = "NULL") var nightNoise: Int?
    ) {
        fun toJson(): String {
            return json.stringify(serializer(), this)
        }
        companion object {
            fun fromJson(jsonString: String): Colors {
                return json.parse(serializer(), jsonString)
            }
        }
    }

    @Serializable
    data class TextDisplaySettings(
        @Embedded(prefix="margin_size_") var marginSize: MarginSize? = null,
        @Embedded(prefix="colors_") var colors: Colors? = null,
        @ColumnInfo(defaultValue = "NULL") var showStrongs: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showMorphology: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showFootNotes: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showRedLetters: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showSectionTitles: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showVerseNumbers: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showVersePerLine: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showBookmarks: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var showMyNotes: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var justifyText: Boolean? = null,
        @ColumnInfo(defaultValue = "NULL") var hyphenation: Boolean? = null,
        @Embedded(prefix="font_") var font: Font? = null,
        @ColumnInfo(defaultValue = "NULL") var lineSpacing: Int? = null
    ) {
        enum class Types {
            FONT,
            COLORS,
            MARGINSIZE,
            JUSTIFY,
            HYPHENATION,
            LINE_SPACING,
            STRONGS,
            MORPH,
            FOOTNOTES,
            REDLETTERS,
            SECTIONTITLES,
            VERSENUMBERS,
            VERSEPERLINE,
            BOOKMARKS,
            MYNOTES,
        }

        fun getValue(type: Types): Any? = when(type) {
            Types.STRONGS -> showStrongs
            Types.MORPH -> showMorphology
            Types.FOOTNOTES -> showFootNotes
            Types.REDLETTERS -> showRedLetters
            Types.SECTIONTITLES -> showSectionTitles
            Types.VERSENUMBERS -> showVerseNumbers
            Types.VERSEPERLINE -> showVersePerLine
            Types.BOOKMARKS -> showBookmarks
            Types.MYNOTES -> showMyNotes
            Types.MARGINSIZE -> marginSize?.copy()
            Types.COLORS -> colors?.copy()
            Types.JUSTIFY -> justifyText
            Types.HYPHENATION -> hyphenation
            Types.LINE_SPACING -> lineSpacing
            Types.FONT -> font?.copy()
        }

        fun setValue(type: Types, value: Any?) {
            when(type) {
                Types.STRONGS -> showStrongs = value as Boolean?
                Types.MORPH -> showMorphology = value as Boolean?
                Types.FOOTNOTES -> showFootNotes = value as Boolean?
                Types.REDLETTERS -> showRedLetters = value as Boolean?
                Types.SECTIONTITLES -> showSectionTitles = value as Boolean?
                Types.VERSENUMBERS -> showVerseNumbers = value as Boolean?
                Types.VERSEPERLINE -> showVersePerLine = value as Boolean?
                Types.BOOKMARKS -> showBookmarks = value as Boolean?
                Types.MYNOTES -> showMyNotes = value as Boolean?
                Types.MARGINSIZE -> marginSize = value as MarginSize?
                Types.COLORS -> colors = value as Colors?
                Types.JUSTIFY -> justifyText = value as Boolean?
                Types.HYPHENATION -> hyphenation = value as Boolean?
                Types.FONT -> font = value as Font?
                Types.LINE_SPACING -> lineSpacing = value as Int?
            }
        }

        fun setNonSpecific(type: Types) {
            setValue(type, null)
        }

        fun toJson(): String {
            return json.stringify(serializer(), this)
        }

        fun copyFrom(textDisplaySettings: TextDisplaySettings) {
            for(t in Types.values()) {
                setValue(t, textDisplaySettings.getValue(t))
            }
        }

        companion object {
            fun fromJson(jsonString: String): TextDisplaySettings {
                return json.parse(serializer(), jsonString)
            }

            val default get() = TextDisplaySettings(
                colors = Colors(
                    dayBackground = null,
                    dayTextColor = null,
                    nightBackground = null,
                    nightTextColor = null,
                    nightNoise = 0,
                    dayNoise = 0
                ),
                marginSize = MarginSize(
                    marginLeft = 0,
                    marginRight = 0,
                    maxWidth = 170
                ),
                font = Font(
                    fontSize = 16,
                    fontFamily = "sans-serif"
                ),
                showStrongs = false,
                showMorphology = false,
                showFootNotes = false,
                showRedLetters = false,
                showSectionTitles = true,
                showVerseNumbers = true,
                showVersePerLine = false,
                showBookmarks = true,
                showMyNotes = true,
                justifyText = true,
                hyphenation = true,
                lineSpacing = 16
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

    data class WindowBehaviorSettings(
        @ColumnInfo(defaultValue = "0") var enableTiltToScroll: Boolean = false,
        @ColumnInfo(defaultValue = "0") var enableReverseSplitMode: Boolean = false,
        @ColumnInfo(defaultValue = "1") var autoPin: Boolean = false
    ) {
        companion object {
            val default get() = WindowBehaviorSettings(
                enableTiltToScroll = false,
                enableReverseSplitMode = false,
                autoPin = true
            )
        }
    }

    @Entity
    data class Workspace(
        var name: String,
        var contentsText: String? = null,

        @PrimaryKey(autoGenerate = true) var id: Long = 0,
        @ColumnInfo(defaultValue = "0") var orderNumber: Int = 0,

        @Embedded(prefix="text_display_settings_") var textDisplaySettings: TextDisplaySettings? = TextDisplaySettings(),
        @Embedded(prefix="window_behavior_settings_") val windowBehaviorSettings: WindowBehaviorSettings? = WindowBehaviorSettings(),
        @ColumnInfo(defaultValue = "NULL") var unPinnedWeight: Float? = null,
        val maximizedWindowId: Long? = null
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
            Index("windowId")
        ]
    )

    data class HistoryItem(
        val windowId: Long,
        val createdAt: Date,
        val document: String,
        val key: String,
        val yOffsetRatio: Float?,

        @PrimaryKey(autoGenerate = true) val id: Long = 0
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
        var workspaceId: Long,
        val isSynchronized: Boolean,
        val isPinMode: Boolean,
        val isLinksWindow: Boolean,
        @Embedded(prefix="window_layout_") val windowLayout: WindowLayout,
        @PrimaryKey(autoGenerate = true) var id: Long = 0,
        var orderNumber: Int = 0
    )
}

@Serializable
data class SettingsBundle (
    val workspaceId: Long,
    val workspaceName: String,
    val workspaceSettings: WorkspaceEntities.TextDisplaySettings,
    val pageManagerSettings: WorkspaceEntities.TextDisplaySettings? = null,
    val windowId: Long? = null
) {
    val actualSettings: WorkspaceEntities.TextDisplaySettings get() {
        return if(windowId == null) workspaceSettings else WorkspaceEntities.TextDisplaySettings.actual(pageManagerSettings!!, workspaceSettings)
    }

    fun toJson(): String {
        return json.stringify(serializer(), this)
    }
    companion object {
        fun fromJson(jsonString: String): SettingsBundle {
            return json.parse(serializer(), jsonString)
        }
    }

}

