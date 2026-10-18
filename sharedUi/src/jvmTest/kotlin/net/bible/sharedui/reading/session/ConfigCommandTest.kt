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


package net.bible.sharedui.reading.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities

class ConfigCommandTest {
    private val labelA = IdType("00000000-0000-0000-0000-00000000000a")
    private val labelB = IdType("00000000-0000-0000-0000-00000000000b")

    private fun inputs() = ConfigInputs(
        displaySettings = WorkspaceEntities.TextDisplaySettings(),
        isActive = true,
        isBottomWindow = true,
        hasActiveIndicator = true,
        nightMode = true,
        showErrorBox = true,
        favouriteLabels = listOf(labelA),
        recentLabels = listOf(labelA, labelB),
        studyPadCursors = mapOf(labelB to 7),
        autoAssignLabels = listOf(labelB),
        hideCompareDocuments = setOf("KJV"),
        limitAmbiguousModalSize = true,
        windowDisplayId = "abcde",
        disableBibleModalButtons = setOf("share"),
        disableGenericModalButtons = setOf("copy"),
        monochromeMode = true,
        einkMode = true,
        colorEinkMode = true,
        pureMonochromeMode = true,
        disableAnimations = true,
        fontSizeMultiplier = 1.5f,
        enabledExperimentalFeatures = listOf("exp1"),
        themeColors = """{"primary":"#112233"}""",
        disableClickToEdit = true,
        llmConfigured = true,
        notesContentType = "MARKDOWN",
    )

    private fun noSpace(s: String) = s.replace(" ", "")

    @Test
    fun initialConfigCarriesAllFlags() {
        val js = noSpace(ConfigCommand.build(true, inputs()).js)
        listOf(
            "monochromeMode:true", "einkMode:true", "colorEinkMode:true", "pureMonochromeMode:true",
            "disableAnimations:true", "disableClickToEdit:true", "llmConfigured:true",
            "activeWindow:true", "isBottomWindow:true", "hasActiveIndicator:true", "nightMode:true",
            "errorBox:true", "limitAmbiguousModalSize:true", "initial:true",
            "fontSizeMultiplier:1.5", "windowId:'abcde'", "notesContentType:\"MARKDOWN\"",
            "favouriteLabels:[\"${labelA}\"]", "recentLabels:[\"${labelA}\",\"${labelB}\"]",
            "autoAssignLabels:[\"${labelB}\"]", "studyPadCursors:{\"${labelB}\":7}",
            "hideCompareDocuments:[\"KJV\"]", "disableBibleModalButtons:[\"share\"]",
            "disableGenericModalButtons:[\"copy\"]", "enabledExperimentalFeatures:[\"exp1\"]",
            "themeColors:{\"primary\":\"#112233\"}",
        ).forEach { assertTrue(js.contains(it), "missing $it in $js") }
        assertTrue(js.startsWith("bibleView.emit(\"set_config\","), js)
    }

    @Test
    fun initialFlagFollowsTheArgument() {
        assertTrue(noSpace(ConfigCommand.build(false, inputs()).js).contains("initial:false"))
    }

    @Test
    fun configIsStableForEqualInputs() {
        assertEquals(ConfigCommand.build(true, inputs()), ConfigCommand.build(true, inputs()))
    }
}
